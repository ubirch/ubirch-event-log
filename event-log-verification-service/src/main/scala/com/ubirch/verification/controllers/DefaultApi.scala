package com.ubirch.verification.controllers

import com.google.inject.Inject
import com.typesafe.scalalogging.StrictLogging
import com.ubirch.niomon.cache.RedisCache
import com.ubirch.niomon.healthcheck.{ Checks, HealthCheckServer }
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.verification.controllers.Api.DecoratedResponse.toDecoration
import com.ubirch.verification.controllers.Api.{ Anchors, DecoratedResponse, Failure, NotFound, Response, Success }
import com.ubirch.verification.models._
import com.ubirch.verification.services.eventlog._
import com.ubirch.verification.services.kafka.AcctEventPublishing
import com.ubirch.verification.services.{ KeyServiceBasedVerifier, TokenVerification }
import com.ubirch.verification.util.{ HashHelper, LookupJsonSupport }
import io.udash.rest.raw.JsonValue
import org.json4s.JsonAST.JNull
import org.msgpack.core.MessagePack
import org.redisson.api.RMapCache

import java.io.{ ByteArrayOutputStream, IOException }
import java.nio.charset.StandardCharsets
import javax.inject.{ Named, Singleton }
import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class DefaultApi @Inject() (
    val accounting: AcctEventPublishing,
    val tokenVerification: TokenVerification,
    @Named("Cached") eventLogClient: EventLogClient,
    verifier: KeyServiceBasedVerifier,
    redis: RedisCache,
    healthcheck: HealthCheckServer
)(implicit ec: ExecutionContext) extends Api with Versions with StrictLogging {

  healthcheck.setReadinessCheck(Checks.ok("business-logic"))
  healthcheck.setLivenessCheck(Checks.ok("business-logic"))

  val controllerHelpers = new ControllerHelpers(accounting, tokenVerification)

  private val api = new Api()
  private val api2 = new Api2()

  private val msgPackConfig = new MessagePack.PackerConfig().withStr8FormatSupport(false)
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

  private val uppCache: Option[RMapCache[Array[Byte], String]] =
    try {
      Some(redis.redisson.getMapCache("hashes_payload"))
    } catch {
      case ex: Throwable =>
        logger.error("redis error: ", ex)
        None
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

  private[controllers] def verifyBase(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[DecoratedResponse] = {

    val requestId = HashHelper.bytesToPrintableId(hash)

    val responseFuture = for {
      response <- eventLogClient.getEventByHash(hash, queryDepth, responseForm, blockchainInfo)
      _ = logger.debug(s"[$requestId] received event log response[$queryDepth, $responseForm] : [$response]")
      _ <- controllerHelpers.earlyResponseIf(response == null)(NotFound.withNoPM)
      _ <- controllerHelpers.earlyResponseIf(!response.success)(Failure(errorType = "EventLogError", errorMessage = response.message).withNoPM)

      upp = LookupJsonSupport.FromJson[ProtocolMessage](response.event).get
      _ <- controllerHelpers.earlyResponseIf(!response.success)(Failure(errorType = "EventLogError", errorMessage = response.message).withNoPM)
      _ <- controllerHelpers.earlyResponseIf(upp == null)(NotFound.withNoPM)
      _ <- controllerHelpers.earlyResponseIf(!verifier.verifySuppressExceptions(upp))(Failure().withNoPM)

      anchors = Anchors(JsonValue(LookupJsonSupport.stringify(response.anchors)))
      seal = rawPacket(upp)
      successNoChain = Success(HashHelper.b64(seal), null, anchors)

      _ <- controllerHelpers.earlyResponseIf(upp.getChain == null)(successNoChain.boundPM(upp))

      // overwritting the type of queryDepth to simple (cf UP-1454), as the full path of the chained UPP is not being used right now
      chainResponse <- eventLogClient.getEventBySignature(HashHelper.b64(upp.getChain).getBytes(StandardCharsets.UTF_8), queryDepth = Simple, responseForm, blockchainInfo)
      _ = logger.debug(s"[$requestId] received chain response: [$chainResponse]")

      chainRequestFailed = chainResponse == null || !chainResponse.success || chainResponse.event == null || chainResponse.event == JNull
      _ = if (chainRequestFailed) logger.warn(s"[$requestId] chain request failed even though chain was set in the original packet")
      _ <- controllerHelpers.earlyResponseIf(chainRequestFailed)(successNoChain.boundPM(upp))

      chainUPP = LookupJsonSupport.FromJson[ProtocolMessage](chainResponse.event).get
      chain = rawPacket(chainUPP)
    } yield {
      successNoChain.copy(prev = HashHelper.b64(chain)).boundPM(upp)
    }

    controllerHelpers.finalizeResponse(responseFuture, requestId)
  }

  private[controllers] def lookupBase(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo, disableRedisLookup: Boolean): Future[DecoratedResponse] = {

    val requestId = HashHelper.bytesToPrintableId(hash)

    val responseFuture = for {
      cachedUpp <- if (disableRedisLookup) Future.successful(None) else findCachedUpp(hash)
      _ <- controllerHelpers.earlyResponseIf(cachedUpp.isDefined)(Success(cachedUpp.get, null, JsonValue("null")).withNoPM)

      response <- eventLogClient.getEventByHash(hash, queryDepth, responseForm, blockchainInfo)
      _ = logger.debug(s"[$requestId] received event log response: [$response]")
      _ <- controllerHelpers.earlyResponseIf(response == null)(NotFound.withNoPM)
      _ <- controllerHelpers.earlyResponseIf(!response.success)(Failure(errorType = "EventLogError", errorMessage = response.message).withNoPM)

      upp = LookupJsonSupport.FromJson[ProtocolMessage](response.event).get
      _ <- controllerHelpers.earlyResponseIf(upp == null)(NotFound.withNoPM)

      anchors = Anchors(JsonValue(LookupJsonSupport.stringify(response.anchors)))
      seal = rawPacket(upp)

    } yield Success(HashHelper.b64(seal), null, anchors).boundPM(upp)

    controllerHelpers.finalizeResponse(responseFuture, requestId)

  }

  override def health: Future[String] = healthCheck

  //V1
  override def getUPP(hash: Array[Byte], disableRedisLookup: Boolean): Future[Response] = api.getUPP(hash, disableRedisLookup)
  override def verifyUPP(hash: Array[Byte]): Future[Response] = api.verifyUPP(hash)
  override def verifyUPPWithUpperBound(hash: Array[Byte], responseForm: String, blockchainInfo: String): Future[Response] =
    api.verifyUPPWithUpperBound(hash, responseForm, blockchainInfo)
  override def verifyUPPWithUpperAndLowerBound(hash: Array[Byte], responseForm: String, blockchainInfo: String): Future[Response] =
    api.verifyUPPWithUpperAndLowerBound(hash, responseForm, blockchainInfo)

  //V2
  override def getUPPV2(hash: Array[Byte], disableRedisLookup: Boolean, authToken: String, originHeader: String): Future[Response] =
    api2.getUPP(hash, disableRedisLookup, authToken, originHeader)
  override def verifyUPPV2(hash: Array[Byte], authToken: String, originHeader: String): Future[Response] =
    api2.verifyUPP(hash, authToken, originHeader)
  override def verifyUPPWithUpperBoundV2(hash: Array[Byte], responseForm: String, blockchainInfo: String, authToken: String, originHeader: String): Future[Response] =
    api2.verifyUPPWithUpperBound(hash, responseForm, blockchainInfo, authToken, originHeader)
  override def verifyUPPWithUpperAndLowerBoundV2(hash: Array[Byte], responseForm: String, blockchainInfo: String, authToken: String, originHeader: String): Future[Response] =
    api2.verifyUPPWithUpperAndLowerBound(hash, responseForm, blockchainInfo, authToken, originHeader)

}
