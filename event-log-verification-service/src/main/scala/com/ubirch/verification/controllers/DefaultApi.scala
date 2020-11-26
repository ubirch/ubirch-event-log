package com.ubirch.verification.controllers

import java.io.{ ByteArrayOutputStream, IOException }
import java.nio.charset.StandardCharsets
import java.util.Date

import com.google.inject.Inject
import com.typesafe.scalalogging.StrictLogging
import com.ubirch.niomon.cache.RedisCache
import com.ubirch.niomon.healthcheck.{ Checks, HealthCheckServer }
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.verification.controllers.Api.DecoratedResponse.toDecoration
import com.ubirch.verification.controllers.Api.{ Anchors, AuthorizationHeaderNotFound, DecoratedResponse, Failure, Forbidden, NotFound, Response, Success }
import com.ubirch.verification.models._
import com.ubirch.verification.services.eventlog._
import com.ubirch.verification.services.kafka.AcctEventPublishing
import com.ubirch.verification.services.{ Content, KeyServiceBasedVerifier, TokenVerification }
import com.ubirch.verification.util.Exceptions.InvalidSpecificClaim
import com.ubirch.verification.util.{ HashHelper, LookupJsonSupport }
import io.prometheus.client.{ Counter, Summary }
import io.udash.rest.raw.{ HttpErrorException, JsonValue }
import javax.inject.{ Named, Singleton }
import org.json4s.JsonAST.JNull
import org.msgpack.core.MessagePack
import org.redisson.api.RMapCache

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NoStackTrace

@Singleton
class DefaultApi @Inject() (
    accounting: AcctEventPublishing,
    tokenVerification: TokenVerification,
    @Named("Cached") eventLogClient: EventLogClient,
    verifier: KeyServiceBasedVerifier,
    redis: RedisCache,
    healthcheck: HealthCheckServer
)(implicit ec: ExecutionContext) extends Api with StrictLogging {

  private val processingTimer: Summary = Summary
    .build("processing_time_seconds", "Message processing time in seconds")
    .labelNames("service", "end_point")
    .quantile(0.9, 0.05)
    .quantile(0.95, 0.05)
    .quantile(0.99, 0.05)
    .quantile(0.999, 0.05)
    .register()

  private val requestReceived: Counter = Counter
    .build("http_requests_count", "Number of http request received.")
    .register()

  private val responsesSent: Counter = Counter
    .build("http_responses_count", "Number of http responses sent.")
    .labelNames("status")
    .register()

  private val uppCache: Option[RMapCache[Array[Byte], String]] =
    try {
      Some(redis.redisson.getMapCache("hashes_payload"))
    } catch {
      case ex: Throwable =>
        logger.error("redis error: ", ex)
        None
    }

  private val msgPackConfig = new MessagePack.PackerConfig().withStr8FormatSupport(false)

  healthcheck.setReadinessCheck(Checks.ok("business-logic"))
  healthcheck.setLivenessCheck(Checks.ok("business-logic"))

  private def rawPacket(upp: ProtocolMessage): Array[Byte] = {
    val out = new ByteArrayOutputStream(255)
    val packer = msgPackConfig.newPacker(out)

    packer.writePayload(upp.getSigned)
    packer.packBinaryHeader(upp.getSignature.length)
    packer.writePayload(upp.getSignature)
    packer.flush()
    packer.close()

    out.toByteArray
  }

  /** little utility function that makes the DSL below readable */
  private def earlyResponseIf(condition: Boolean)(response: => DecoratedResponse): Future[Unit] =
    if (condition) Future.failed(ResponseException(response)) else Future.unit

  /** exception for wrapping early responses in the DSL below */
  case class ResponseException(resp: DecoratedResponse) extends Exception with NoStackTrace

  private def finalizeResponse(responseFuture: Future[DecoratedResponse], requestId: String): Future[DecoratedResponse] =
    responseFuture.recover {
      case ResponseException(response) => response
      case e: Exception =>
        logger.error(s"[$requestId] unexpected exception", e)
        throw HttpErrorException(500, payload = s"InternalServerError: ${e.getClass.getSimpleName}: ${e.getMessage}", cause = e)
    }

  private def verifyBase(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[DecoratedResponse] = {

    val requestId = HashHelper.bytesToPrintableId(hash)

    val responseFuture = for {
      response <- eventLogClient.getEventByHash(hash, queryDepth, responseForm, blockchainInfo)
      _ = logger.debug(s"[$requestId] received event log response[$queryDepth, $responseForm] : [$response]")
      _ <- earlyResponseIf(response == null)(NotFound.withNoPM)
      _ <- earlyResponseIf(!response.success)(Failure(errorType = "EventLogError", errorMessage = response.message).withNoPM)

      //TODO ADD TRY HERE
      upp = LookupJsonSupport.FromJson[ProtocolMessage](response.event).get
      _ <- earlyResponseIf(!response.success)(Failure(errorType = "EventLogError", errorMessage = response.message).withNoPM)
      _ <- earlyResponseIf(upp == null)(NotFound.withNoPM)
      _ <- earlyResponseIf(!verifier.verifySuppressExceptions(upp))(Failure().withNoPM)

      anchors = Anchors(JsonValue(LookupJsonSupport.stringify(response.anchors)))
      seal = rawPacket(upp)
      successNoChain = Success(HashHelper.b64(seal), null, anchors)

      _ <- earlyResponseIf(upp.getChain == null)(successNoChain.boundPM(upp))

      // overwritting the type of queryDepth to simple (cf UP-1454), as the full path of the chained UPP is not being used right now
      chainResponse <- eventLogClient.getEventBySignature(HashHelper.b64(upp.getChain).getBytes(StandardCharsets.UTF_8), queryDepth = Simple, responseForm, blockchainInfo)
      _ = logger.debug(s"[$requestId] received chain response: [$chainResponse]")

      chainRequestFailed = chainResponse == null || !chainResponse.success || chainResponse.event == null || chainResponse.event == JNull
      _ = if (chainRequestFailed) logger.warn(s"[$requestId] chain request failed even though chain was set in the original packet")
      _ <- earlyResponseIf(chainRequestFailed)(successNoChain.boundPM(upp))

      chainUPP = LookupJsonSupport.FromJson[ProtocolMessage](chainResponse.event).get
      chain = rawPacket(chainUPP)
    } yield {
      successNoChain.copy(prev = HashHelper.b64(chain)).boundPM(upp)
    }

    finalizeResponse(responseFuture, requestId)
  }

  private def findCachedUpp(bytes: Array[Byte]): Future[Option[String]] = {

    Future.successful {
      Option(
        uppCache
          .getOrElse(throw new IOException("uppCache couldn't become retrieved properly"))
          .get(bytes)
      )
    }.recover {
      case ex: Throwable =>
        logger.error("redis error ", ex)
        None
    }
  }

  private def lookupBase(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo, disableRedisLookup: Boolean): Future[DecoratedResponse] = {

    val requestId = HashHelper.bytesToPrintableId(hash)

    val responseFuture = for {
      cachedUpp <- if (disableRedisLookup) Future.successful(None) else findCachedUpp(hash)
      _ <- earlyResponseIf(cachedUpp.isDefined)(Success(cachedUpp.get, null, JsonValue("null")).withNoPM)

      response <- eventLogClient.getEventByHash(hash, queryDepth, responseForm, blockchainInfo)
      _ = logger.debug(s"[$requestId] received event log response: [$response]")
      _ <- earlyResponseIf(response == null)(NotFound.withNoPM)
      _ <- earlyResponseIf(!response.success)(Failure(errorType = "EventLogError", errorMessage = response.message).withNoPM)

      upp = LookupJsonSupport.FromJson[ProtocolMessage](response.event).get
      _ <- earlyResponseIf(upp == null)(NotFound.withNoPM)

      anchors = Anchors(JsonValue(LookupJsonSupport.stringify(response.anchors)))
      seal = rawPacket(upp)

    } yield Success(HashHelper.b64(seal), null, anchors).boundPM(upp)

    finalizeResponse(responseFuture, requestId)
  }

  private def registerMetrics[T](endpoint: String)(f: () => Future[T]): Future[T] = {
    val timer = processingTimer.labels("verification", endpoint).startTimer()
    f().transform { r => requestReceived.inc(); timer.observeDuration(); r }
      .transform { r =>
        r match {
          case util.Success(value: Response) =>
            value match {
              case Success(_, _, _) => responsesSent.labels(Response.OK.toString).inc()
              case NotFound => responsesSent.labels(Response.NOT_FOUND.toString).inc()
              case AuthorizationHeaderNotFound => responsesSent.labels(Response.UNAUTHORIZED.toString).inc()
              case Forbidden => responsesSent.labels(Response.FORBIDDEN.toString).inc()
              case Failure(_, _, _, _) => responsesSent.labels(Response.BAD_REQUEST.toString).inc()

            }
          case util.Success(_) => responsesSent.labels(Response.OK.toString).inc()
          case util.Failure(_) => responsesSent.labels(Response.BAD_REQUEST.toString).inc()
        }
        r
      }

  }

  private def registerAcctEvent[T](accessInfo: (Map[String, Any], Content))(f: => Future[DecoratedResponse]): Future[Response] = {
    import TokenVerification._

    val (all, content) = accessInfo

    f.transform { r =>

      val res: scala.util.Try[Response] = for {
        owner <- all.getSubject
        DecoratedResponse(maybeUPP, response) <- r
      } yield {

        maybeUPP match {
          case Some(upp) =>
            if (content.targetIdentities.contains(upp.getUUID.toString)) {

              response match {
                case Success(_, _, _) =>
                  accounting
                    .publish_!(AcctEvent(java.util.UUID.randomUUID(), owner, Option(upp.getUUID), "verification", Some(content.purpose), new Date()))
                case _ => // Do nothing

              }

              response

            } else {
              logger.warn("upp_uuid_not_equals_target_identities {} {}", upp.getUUID, content.targetIdentities)
              Forbidden
            }
          case None => response
        }

      }

      res.recover {
        case e: InvalidSpecificClaim =>
          logger.error(s"error_getting_owner_token=${e.getMessage}", e)
          Forbidden
        case e: Exception =>
          logger.error(s"unknown_token=${e.getMessage}", e)
          Failure()
      }

    }

  }

  private def getToken(token: String): Option[(Map[String, Any], Content)] = token.split(" ").toList match {
    case List(x, y) =>
      val isBearer = x.toLowerCase == "bearer"
      val token = tokenVerification.decodeAndVerify(y)
      if (isBearer && token.isDefined) {
        token
      } else {
        None
      }

    case _ => None
  }

  private def authorization[T](authToken: String)(f: ((Map[String, Any], Content)) => Future[Response]): () => Future[Response] = {
    lazy val token = getToken(authToken)
    authToken match {
      case "No-Header-Found" => () => Future.successful(AuthorizationHeaderNotFound)
      case _ if token.isDefined => () => f(token.get)
      case _ => () => Future.successful(Forbidden)
    }
  }

  override def health: Future[String] = {
    registerMetrics("health") { () =>
      Future.successful("ok")
    }
  }

  //V1

  override def getUPP(hash: Array[Byte], disableRedisLookup: Boolean): Future[Response] = {
    registerMetrics("upp") { () =>
      lookupBase(hash, Simple, AnchorsNoPath, Normal, disableRedisLookup).map(_.response)
    }
  }

  override def verifyUPP(hash: Array[Byte]): Future[Response] = {
    registerMetrics("simple") { () =>
      verifyBase(
        hash,
        Simple,
        AnchorsNoPath,
        Normal
      ).map(_.response)
    }
  }

  override def verifyUPPWithUpperBound(hash: Array[Byte], responseForm: String, blockchainInfo: String): Future[Response] = {
    registerMetrics("anchor") { () =>
      verifyBase(
        hash,
        ShortestPath,
        ResponseForm.fromString(responseForm).getOrElse(AnchorsNoPath),
        BlockchainInfo.fromString(blockchainInfo).getOrElse(Normal)
      ).map(_.response)
    }
  }

  override def verifyUPPWithUpperAndLowerBound(hash: Array[Byte], responseForm: String, blockchainInfo: String): Future[Response] = {
    registerMetrics("record") { () =>
      verifyBase(
        hash,
        UpperLower,
        ResponseForm.fromString(responseForm).getOrElse(AnchorsNoPath),
        BlockchainInfo.fromString(blockchainInfo).getOrElse(Normal)
      ).map(_.response)
    }
  }

  //V2
  override def getUPPV2(hash: Array[Byte], disableRedisLookup: Boolean, authToken: String): Future[Response] = {
    registerMetrics("v2.upp") {
      authorization(authToken) { accessInfo =>
        registerAcctEvent(accessInfo) {
          lookupBase(hash, Simple, AnchorsNoPath, Normal, disableRedisLookup)
        }
      }
    }
  }

  override def verifyUPPV2(hash: Array[Byte], authToken: String): Future[Response] = {
    registerMetrics("v2.simple") {
      authorization(authToken) { accessInfo =>
        registerAcctEvent(accessInfo) {
          verifyBase(
            hash,
            Simple,
            AnchorsNoPath,
            Normal
          )
        }
      }
    }
  }

  override def verifyUPPWithUpperBoundV2(hash: Array[Byte], responseForm: String, blockchainInfo: String, authToken: String): Future[Response] = {
    registerMetrics("v2.anchor") {
      authorization(authToken) { accessInfo =>
        registerAcctEvent(accessInfo) {
          verifyBase(
            hash,
            ShortestPath,
            ResponseForm.fromString(responseForm).getOrElse(AnchorsNoPath),
            BlockchainInfo.fromString(blockchainInfo).getOrElse(Normal)
          )
        }
      }
    }
  }

  override def verifyUPPWithUpperAndLowerBoundV2(hash: Array[Byte], responseForm: String, blockchainInfo: String, authToken: String): Future[Response] = {
    registerMetrics("v2.record") {
      authorization(authToken) { accessInfo =>
        registerAcctEvent(accessInfo) {
          verifyBase(
            hash,
            UpperLower,
            ResponseForm.fromString(responseForm).getOrElse(AnchorsNoPath),
            BlockchainInfo.fromString(blockchainInfo).getOrElse(Normal)
          )
        }
      }
    }
  }

}
