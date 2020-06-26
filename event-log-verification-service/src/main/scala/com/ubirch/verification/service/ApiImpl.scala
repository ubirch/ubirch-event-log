package com.ubirch.verification.service

import java.io.{ByteArrayOutputStream, IOException}
import java.nio.charset.StandardCharsets
import java.util.Base64

import com.google.inject.Inject
import com.typesafe.scalalogging.StrictLogging
import com.ubirch.niomon.cache.RedisCache
import com.ubirch.niomon.healthcheck.{Checks, HealthCheckServer}
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.verification.service.Api.{Failure, NotFound, Response, Success}
import com.ubirch.verification.service.models._
import com.ubirch.verification.service.services.eventlog._
import io.udash.rest.raw.{HttpErrorException, JsonValue}
import javax.inject.{Named, Singleton}
import org.msgpack.core.MessagePack
import org.redisson.api.RMapCache

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NoStackTrace

@Singleton
class ApiImpl @Inject()(@Named("Cached") eventLogClient: EventLogClient, verifier: KeyServiceBasedVerifier,
                        redis: RedisCache, healthcheck: HealthCheckServer) extends Api with StrictLogging {


  private val uppCache: Option[RMapCache[Array[Byte], String]] =
    try {
      Some(redis.redisson.getMapCache("verifier-upp-cache"))
    } catch {
      case ex: Throwable => logger.error("redis error: ", ex)
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

  private def b64(x: Array[Byte]): String = if (x != null) Base64.getEncoder.encodeToString(x) else null

  private def bytesToPrintableId(x: Array[Byte]): String =
    if (x.exists(c => !(c.toChar.isLetterOrDigit || c == '=' || c == '/' || c == '+'))) b64(x)
    else new String(x, StandardCharsets.UTF_8)

  /** little utility function that makes the DSL below readable */
  private def earlyResponseIf(condition: Boolean)(response: => Response): Future[Unit] =
    if (condition) Future.failed(ResponseException(response)) else Future.unit

  /** exception for wrapping early responses in the DSL below */
  case class ResponseException(resp: Response) extends Exception with NoStackTrace

  def finalizeResponse(responseFuture: Future[Api.Success], requestId: String)(implicit ec: ExecutionContext): Future[Response] =
    responseFuture.recover {
      case ResponseException(response) => response
      case e: Exception =>
        logger.error(s"[$requestId] unexpected exception", e)
        throw HttpErrorException(500, payload = s"InternalServerError: ${e.getClass.getSimpleName}: ${e.getMessage}", cause = e)
    }

  def verifyBase(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[Response] = {
    implicit val ec: ExecutionContext = ExecutionContext.global

    val requestId = bytesToPrintableId(hash)

    val responseFuture = for {
      response: EventLogClient.Response <- eventLogClient.getEventByHash(hash, queryDepth, responseForm, blockchainInfo)
      _ = logger.debug(s"[$requestId] received event log response[$queryDepth, $responseForm] : [$response]")
      _ <- earlyResponseIf(response == null)(NotFound)
      _ <- earlyResponseIf(!response.success)(Failure(errorType = "EventLogError", errorMessage = response.message))

      upp = response.data.event
      _ <- earlyResponseIf(upp == null)(NotFound)
      _ <- earlyResponseIf(!verifier.verifySuppressExceptions(upp))(Failure())

      anchors = response.data.anchors
      seal = rawPacket(upp)
      successNoChain = Success(b64(seal), null, anchors)

      _ <- earlyResponseIf(upp.getChain == null)(successNoChain)

      // overwritting the type of queryDepth to simple (cf UP-1454), as the full path of the chained UPP is not being used right now
      chainResponse <- eventLogClient.getEventBySignature(b64(upp.getChain).getBytes(StandardCharsets.UTF_8), queryDepth = Simple, responseForm, blockchainInfo)
      _ = logger.debug(s"[$requestId] received chain response: [$chainResponse]")

      chainRequestFailed = chainResponse == null || !chainResponse.success || chainResponse.data.event == null
      _ = if (chainRequestFailed) logger.warn(s"[$requestId] chain request failed even though chain was set in the original packet")
      _ <- earlyResponseIf(chainRequestFailed)(successNoChain)

      chain = rawPacket(chainResponse.data.event)
    } yield {
      successNoChain.copy(prev = b64(chain))
    }

    finalizeResponse(responseFuture, requestId)
  }

  def findCachedUpp(bytes: Array[Byte]): Future[Option[String]] = {

    implicit val ec: ExecutionContext = ExecutionContext.global

    Future.successful {
      Option(
        uppCache
          .getOrElse(throw new IOException("uppCache couldn't become retrieved properly"))
          .get(bytes))
    }.recover {
      case ex: Throwable =>
        logger.error("redis error ", ex)
        None
    }
  }


  def lookupBase(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo, disableRedisLookup: Boolean): Future[Response] = {
    implicit val ec: ExecutionContext = ExecutionContext.global

    val requestId = bytesToPrintableId(hash)

    val responseFuture = for {
      cachedUpp <- if (disableRedisLookup) Future.successful(None) else findCachedUpp(hash)
      _ <- earlyResponseIf(cachedUpp.isDefined)(Success(cachedUpp.get, null, JsonValue("null")))

      response <- eventLogClient.getEventByHash(hash, queryDepth, responseForm, blockchainInfo)
      _ = logger.debug(s"[$requestId] received event log response: [$response]")
      _ <- earlyResponseIf(response == null)(NotFound)
      _ <- earlyResponseIf(!response.success)(Failure(errorType = "EventLogError", errorMessage = response.message))

      upp = response.data.event
      _ <- earlyResponseIf(upp == null)(NotFound)

      anchors = response.data.anchors
      seal = rawPacket(upp)

    } yield Success(b64(seal), null, anchors)

    finalizeResponse(responseFuture, requestId)
  }

  override def health: Future[String] = Future.successful("ok")

  override def getUPP(hash: Array[Byte], disableRedisLookup: Boolean): Future[Api.Response] = lookupBase(hash, Simple, AnchorsNoPath, Normal, disableRedisLookup)

  override def verifyUPP(hash: Array[Byte]): Future[Response] =
    verifyBase(
      hash,
      Simple,
      AnchorsNoPath,
      Normal
    )

  override def verifyUPPWithUpperBound(hash: Array[Byte], responseForm: String, blockchainInfo: String): Future[Response] =
    verifyBase(
      hash,
      ShortestPath,
      ResponseForm.fromString(responseForm).getOrElse(AnchorsNoPath),
      BlockchainInfo.fromString(blockchainInfo).getOrElse(Normal)
    )

  override def verifyUPPWithUpperAndLowerBound(hash: Array[Byte], responseForm: String, blockchainInfo: String): Future[Response] =
    verifyBase(
      hash,
      UpperLower,
      ResponseForm.fromString(responseForm).getOrElse(AnchorsNoPath),
      BlockchainInfo.fromString(blockchainInfo).getOrElse(Normal)
    )

}
