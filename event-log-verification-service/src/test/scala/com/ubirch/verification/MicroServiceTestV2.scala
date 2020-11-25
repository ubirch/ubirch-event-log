package com.ubirch.verification

import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.{ Base64, Date, UUID }

import com.typesafe.config.{ Config, ConfigFactory, ConfigValueFactory }
import com.ubirch.client.util.curveFromString
import com.ubirch.crypto.{ GeneratorKeyFactory, PubKey }
import com.ubirch.niomon.cache.RedisCache
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.services.lifeCycle.DefaultLifecycle
import com.ubirch.verification.controllers.Api.{ Anchors, Failure, Success }
import com.ubirch.verification.controllers.{ Api, DefaultApi }
import com.ubirch.verification.models._
import com.ubirch.verification.services._
import com.ubirch.verification.services.eventlog.EventLogClient
import com.ubirch.verification.services.kafka.DefaultAcctEventPublishing
import com.ubirch.verification.util.{ HashHelper, LookupJsonSupport }
import io.prometheus.client.CollectorRegistry
import io.udash.rest.raw.JsonValue
import monix.eval.Task
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.scalatest._
import redis.embedded.RedisServer

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{ Await, Future }

class MicroServiceTestV2 extends FlatSpec with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {

  val anchors = LookupJsonSupport.getJValue {
    """
      |[
      |  {
      |    "status":"added",
      |    "txid":"51f6cfe400bd1062f8fcde5dc5c23aaac111e8124886ecf1f60c33015a35ccb0",
      |    "message":"e392457bdd63db37d00435bfdc0a0a7f4a85f3664b9439956a4f4f2310fd934df85ea4a02823d4674c891f224bcab8c8f2c117fdc8710ce78c928fc9de8d9e19",
      |    "blockchain":"ethereum",
      |    "network_info":"Rinkeby Testnet Network",
      |    "network_type":"testnet",
      |    "created":"2019-05-07T21:30:14.421095"
      |  }
      |]""".stripMargin
  }

  val upp =
    LookupJsonSupport.getJValue {
      """
        |{
        |  "hint":0,
        |  "payload":"c29tZSBieXRlcyEAAQIDnw==",
        |  "signature":"5aTelLQBerVT/vJiL2qjZCxWxqlfwT/BaID0zUVy7LyUC9nUdb02//aCiZ7xH1HglDqZ0Qqb7GyzF4jtBxfSBg==",
        |  "signed":"lRKwjni1ymWXEeiBhcg+pwAOTQCwc29tZSBieXRlcyEAAQIDnw==",
        |  "uuid":"840b7e21-03e9-4de7-bb31-0b9524f3b500",
        |  "version":34
        |}""".stripMargin
    }

  val uppWithChain = LookupJsonSupport.getJValue {
    """
      |{
      |  "hint":0,
      |  "payload":"c29tZSBieXRlcyEAAQIDnw==",
      |  "signature":"5aTelLQBerVT/vJiL2qjZCxWxqlfwT/BaID0zUVy7LyUC9nUdb02//aCiZ7xH1HglDqZ0Qqb7GyzF4jtBxfSBg==",
      |  "signed":"lRKwjni1ymWXEeiBhcg+pwAOTQCwc29tZSBieXRlcyEAAQIDnw==",
      |  "uuid":"840b7e21-03e9-4de7-bb31-0b9524f3b500",
      |  "version":34,
      |  "chain":"lRKwjni1ymWXEeiBhcg+pwAOTQCwc29tZSBieXRlcyEAAQIDnw=="
      |}""".stripMargin
  }

  val cert: PubKey = GeneratorKeyFactory.getPubKey(
    Base64.getDecoder.decode("l/KJeVnO8xTXkW7bjf+OumE7vXxBIkPHg85/uVAbBiY="),
    curveFromString("ECC_ED25519")
  )

  val keyServiceConfig: Config = ConfigFactory
    .empty()
    .withValue("ubirchKeyService.client.rest.host", ConfigValueFactory.fromAnyRef("abcd"))

  val keyService: KeyServerClient = new KeyServerClient(keyServiceConfig) {
    override def getPublicKey(uuid: UUID): List[PubKey] = List(cert)
  }

  val aToken = "bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiJ9.eyJpc3MiOiJodHRwczovL3Rva2VuLmRldi51YmlyY2guY29tIiwic3ViIjoiOTYzOTk1ZWQtY2UxMi00ZWE1LTg5ZGMtYjE4MTcwMWQxZDdiIiwiYXVkIjoiaHR0cHM6Ly92ZXJpZnkuZGV2LnViaXJjaC5jb20iLCJleHAiOjc5MTU4MTI5MDQsImlhdCI6MTYwNDQyMjUwNCwianRpIjoiYmYxYzk4NTktNjk4NC00ZDIzLWIzODUtNTVjZjc0MTA0NDI3IiwicHVycG9zZSI6IktpbmcgRHVkZSAtIENvbmNlcnQiLCJ0YXJnZXRfaWRlbnRpdHkiOiI4NDBiN2UyMS0wM2U5LTRkZTctYmIzMS0wYjk1MjRmM2I1MDAiLCJyb2xlIjoidmVyaWZpZXIifQ.yEIv1Hm4Gtc2QbhT7QcnLoG3IGSPD3J43TAdSfEhUFVHVJ1C5vG0LizzyWG0siedMOkbjdLiMUmqKZPdbqz74A"

  "DefaultApiV2" should "successfully validate handle a valid packet" in {
    val eventLog: EventLogClient = new EventLogClient {
      override def getEventByHash(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupResult] = {
        Future.successful(LookupResult.Found(value = HashHelper.bytesToPrintableId(hash), Payload, upp, anchors))
      }

      override def getEventBySignature(signature: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupResult] = {
        Future.successful(LookupResult.Found(value = HashHelper.bytesToPrintableId(signature), Signature, upp, anchors))
      }
    }

    val config = ConfigFactory.load()
    val redisCache = new RedisCache("test", config)
    val healthCheck = new HealthCheckProvider(config).get()
    val tokenPublicKey = new DefaultTokenPublicKey(config)
    val tokenVerification = new DefaultTokenVerification(config, tokenPublicKey)
    val lifecycle = new DefaultLifecycle()
    val acct = new DefaultAcctEventPublishing(config, lifecycle)
    val api = new DefaultApi(acct, tokenVerification, eventLog, new KeyServiceBasedVerifier(keyService), redisCache, healthCheck)

    val res = Await.result(api.verifyUPPWithUpperBoundV2("c29tZSBieXRlcyEAAQIDnw==".getBytes(StandardCharsets.UTF_8), authToken = aToken), 10.seconds)
    res should equal(Success(
      "lRKwjni1ymWXEeiBhcg+pwAOTQCwc29tZSBieXRlcyEAAQIDn8RA5aTelLQBerVT/vJiL2qjZCxWxqlfwT/BaID0zUVy7LyUC9nUdb02//aCiZ7xH1HglDqZ0Qqb7GyzF4jtBxfSBg==",
      null,
      Anchors(JsonValue(LookupJsonSupport.stringify(anchors)))
    ))
  }

  it should "return an error when handling an invalid packet" in {
    val eventLog: EventLogClient = new EventLogClient {

      val _upp = {
        val u = LookupJsonSupport.FromJson[ProtocolMessage](upp).get
        u.getSignature()(0) = (u.getSignature()(0) + 1).toByte
        LookupJsonSupport.ToJson[ProtocolMessage](u).get
      }

      override def getEventByHash(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupResult] = {
        Future.successful(LookupResult.Found(value = HashHelper.bytesToPrintableId(hash), Payload, _upp, anchors))
      }

      override def getEventBySignature(signature: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupResult] = {
        Future.successful(LookupResult.Found(value = HashHelper.bytesToPrintableId(signature), Signature, _upp, anchors))
      }

    }

    val config = ConfigFactory.load().withValue("verification.health-check.port", ConfigValueFactory.fromAnyRef(PortGiver.giveMeHealthCheckPort))
    val redisCache = new RedisCache("test", config)
    val healthCheck = new HealthCheckProvider(config).get()
    val tokenPublicKey = new DefaultTokenPublicKey(config)
    val tokenVerification = new DefaultTokenVerification(config, tokenPublicKey)
    val lifecycle = new DefaultLifecycle()
    val acct = new DefaultAcctEventPublishing(config, lifecycle)
    val api = new DefaultApi(acct, tokenVerification, eventLog, new KeyServiceBasedVerifier(keyService), redisCache, healthCheck)

    val res = Await.result(api.verifyUPPWithUpperBoundV2("c29tZSBieXRlcyEAAQIDnw==".getBytes(StandardCharsets.UTF_8), authToken = aToken), 10.seconds)

    res should equal(Failure())

  }

  it should "return NotFound error when handling non-existent packet" in {
    val eventLog: EventLogClient = new EventLogClient {
      override def getEventByHash(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupResult] = Future.successful(null)

      override def getEventBySignature(signature: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupResult] = Future.successful(null)
    }
    val config = ConfigFactory.load().withValue("verification.health-check.port", ConfigValueFactory.fromAnyRef(PortGiver.giveMeHealthCheckPort))
    val redisCache = new RedisCache("test", config)
    val healthCheck = new HealthCheckProvider(config).get()
    val tokenPublicKey = new DefaultTokenPublicKey(config)
    val tokenVerification = new DefaultTokenVerification(config, tokenPublicKey)
    val lifecycle = new DefaultLifecycle()
    val acct = new DefaultAcctEventPublishing(config, lifecycle)
    val api = new DefaultApi(acct, tokenVerification, eventLog, new KeyServiceBasedVerifier(keyService), redisCache, healthCheck)

    val res = Await.result(api.verifyUPPV2("c29tZSBieXRlcyEAAQIDnw==".getBytes(StandardCharsets.UTF_8), authToken = aToken), 10.seconds)

    res should equal(Api.NotFound)

  }

  it should "return NotFound error when handling non-existent packet 2" in {
    val eventLog: EventLogClient = new EventLogClient {
      override def getEventByHash(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupResult] = {
        Future.successful(LookupResult.NotFound(HashHelper.bytesToPrintableId(hash), Payload))
      }

      override def getEventBySignature(signature: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupResult] = {
        Future.successful(LookupResult.NotFound(HashHelper.bytesToPrintableId(signature), Signature))
      }
    }
    val config = ConfigFactory.load().withValue("verification.health-check.port", ConfigValueFactory.fromAnyRef(PortGiver.giveMeHealthCheckPort))
    val redisCache = new RedisCache("test", config)
    val healthCheck = new HealthCheckProvider(config).get()
    val tokenPublicKey = new DefaultTokenPublicKey(config)
    val tokenVerification = new DefaultTokenVerification(config, tokenPublicKey)
    val lifecycle = new DefaultLifecycle()
    val acct = new DefaultAcctEventPublishing(config, lifecycle)
    val api = new DefaultApi(acct, tokenVerification, eventLog, new KeyServiceBasedVerifier(keyService), redisCache, healthCheck)

    val res = Await.result(api.verifyUPPV2("c29tZSBieXRlcyEAAQIDnw==".getBytes(StandardCharsets.UTF_8), authToken = aToken), 10.seconds)

    res should equal(Api.NotFound)

  }

  it should "return a Failure(EventLogError) if there's any errors from event log" in {
    val eventLog: EventLogClient = new EventLogClient {

      override def getEventByHash(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupResult] = {
        Future.successful(LookupResult.Error(HashHelper.bytesToPrintableId(hash), Payload, "plutonium leakage"))
      }

      override def getEventBySignature(signature: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupResult] = {
        Future.successful(LookupResult.Error(HashHelper.bytesToPrintableId(signature), Signature, "plutonium leakage"))
      }
    }

    val config = ConfigFactory.load().withValue("verification.health-check.port", ConfigValueFactory.fromAnyRef(PortGiver.giveMeHealthCheckPort))
    val redisCache = new RedisCache("test", config)
    val healthCheck = new HealthCheckProvider(config).get()
    val tokenPublicKey = new DefaultTokenPublicKey(config)
    val tokenVerification = new DefaultTokenVerification(config, tokenPublicKey)
    val lifecycle = new DefaultLifecycle()
    val acct = new DefaultAcctEventPublishing(config, lifecycle)
    val api = new DefaultApi(acct, tokenVerification, eventLog, new KeyServiceBasedVerifier(keyService), redisCache, healthCheck)

    val res = Await.result(api.verifyUPPWithUpperBoundV2("c29tZSBieXRlcyEAAQIDnw==".getBytes(StandardCharsets.UTF_8), authToken = aToken), 10.seconds)

    res should equal(Failure(errorType = "EventLogError", errorMessage = "plutonium leakage"))
  }

  it should "successfully pass parameters through when default for verifyUPPWithUpperBound" in {

    val wasHere1 = new AtomicBoolean(false)
    val wasHere2 = new AtomicBoolean(false)

    val eventLog: EventLogClient = new EventLogClient {

      override def getEventByHash(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupResult] = {
        wasHere1.set(true)
        assert(wasHere1.get())
        assert(queryDepth == ShortestPath)
        assert(responseForm == AnchorsNoPath)
        assert(blockchainInfo == Normal)
        Future.successful(LookupResult.Found(HashHelper.bytesToPrintableId(hash), Payload, uppWithChain, anchors))
      }

      override def getEventBySignature(sig: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupResult] = {
        wasHere2.set(true)
        assert(wasHere2.get())
        assert(queryDepth == Simple)
        assert(responseForm == AnchorsNoPath)
        assert(blockchainInfo == Normal)
        Future.successful(LookupResult.Found(HashHelper.bytesToPrintableId(sig), Signature, uppWithChain, anchors))
      }
    }

    val config = ConfigFactory.load().withValue("verification.health-check.port", ConfigValueFactory.fromAnyRef(PortGiver.giveMeHealthCheckPort))
    val redisCache = new RedisCache("test", config)
    val healthCheck = new HealthCheckProvider(config).get()
    val tokenPublicKey = new DefaultTokenPublicKey(config)
    val tokenVerification = new DefaultTokenVerification(config, tokenPublicKey)
    val lifecycle = new DefaultLifecycle()
    val acct = new DefaultAcctEventPublishing(config, lifecycle)
    val api = new DefaultApi(acct, tokenVerification, eventLog, new KeyServiceBasedVerifier(keyService), redisCache, healthCheck)

    Await.result(api.verifyUPPWithUpperBoundV2("c29tZSBieXRlcyEAAQIDnw==".getBytes(StandardCharsets.UTF_8), authToken = aToken), 10.seconds)

    assert(wasHere1.get())
    assert(wasHere2.get())

  }

  it should "successfully pass parameters through when default for verifyUPPWithUpperAndLowerBound" in {

    val wasHere1 = new AtomicBoolean(false)
    val wasHere2 = new AtomicBoolean(false)

    val eventLog: EventLogClient = new EventLogClient {

      override def getEventByHash(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupResult] = {
        wasHere1.set(true)
        assert(wasHere1.get())
        assert(queryDepth == UpperLower)
        assert(responseForm == AnchorsNoPath)
        assert(blockchainInfo == Normal)
        Future.successful(LookupResult.Found(HashHelper.bytesToPrintableId(hash), Payload, uppWithChain, anchors))
      }

      override def getEventBySignature(sig: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupResult] = {
        wasHere2.set(true)
        assert(wasHere2.get())
        assert(queryDepth == Simple)
        assert(responseForm == AnchorsNoPath)
        assert(blockchainInfo == Normal)
        Future.successful(LookupResult.Found(HashHelper.bytesToPrintableId(sig), Signature, uppWithChain, anchors))
      }
    }

    val config = ConfigFactory.load().withValue("verification.health-check.port", ConfigValueFactory.fromAnyRef(PortGiver.giveMeHealthCheckPort))
    val redisCache = new RedisCache("test", config)
    val healthCheck = new HealthCheckProvider(config).get()
    val tokenPublicKey = new DefaultTokenPublicKey(config)
    val tokenVerification = new DefaultTokenVerification(config, tokenPublicKey)
    val lifecycle = new DefaultLifecycle()
    val acct = new DefaultAcctEventPublishing(config, lifecycle)
    val api = new DefaultApi(acct, tokenVerification, eventLog, new KeyServiceBasedVerifier(keyService), redisCache, healthCheck)

    Await.result(api.verifyUPPWithUpperAndLowerBoundV2("c29tZSBieXRlcyEAAQIDnw==".getBytes(StandardCharsets.UTF_8), authToken = aToken), 10.seconds)
    assert(wasHere1.get())
    assert(wasHere2.get())

  }

  it should "successfully pass parameters through when default are modified for verifyUPPWithUpperBound" in {

    val wasHere1 = new AtomicBoolean(false)
    val wasHere2 = new AtomicBoolean(false)
    val _responseForm = AnchorsWithPath
    val _blockchainInfo = Extended

    val eventLog: EventLogClient = new EventLogClient {

      override def getEventByHash(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupResult] = {
        wasHere1.set(true)
        assert(wasHere1.get())
        assert(queryDepth == ShortestPath)
        assert(responseForm == _responseForm)
        assert(blockchainInfo == _blockchainInfo)
        Future.successful(LookupResult.Found(HashHelper.bytesToPrintableId(hash), Payload, uppWithChain, anchors))
      }

      override def getEventBySignature(sig: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupResult] = {
        wasHere2.set(true)
        assert(wasHere2.get())
        assert(queryDepth == Simple)
        assert(responseForm == _responseForm)
        assert(blockchainInfo == _blockchainInfo)
        Future.successful(LookupResult.Found(HashHelper.bytesToPrintableId(sig), Signature, uppWithChain, anchors))
      }
    }

    val config = ConfigFactory.load().withValue("verification.health-check.port", ConfigValueFactory.fromAnyRef(PortGiver.giveMeHealthCheckPort))
    val redisCache = new RedisCache("test", config)
    val healthCheck = new HealthCheckProvider(config).get()
    val tokenPublicKey = new DefaultTokenPublicKey(config)
    val tokenVerification = new DefaultTokenVerification(config, tokenPublicKey)
    val lifecycle = new DefaultLifecycle()
    val acct = new DefaultAcctEventPublishing(config, lifecycle)
    val api = new DefaultApi(acct, tokenVerification, eventLog, new KeyServiceBasedVerifier(keyService), redisCache, healthCheck)

    Await.result(api.verifyUPPWithUpperBoundV2("c29tZSBieXRlcyEAAQIDnw==".getBytes(StandardCharsets.UTF_8), _responseForm.value, _blockchainInfo.value, authToken = aToken), 10.seconds)
    assert(wasHere1.get())
    assert(wasHere2.get())

  }

  it should "successfully pass parameters through when default are modified for verifyUPPWithUpperAndLowerBound" in {

    val wasHere1 = new AtomicBoolean(false)
    val wasHere2 = new AtomicBoolean(false)

    val _responseForm = AnchorsWithPath
    val _blockchainInfo = Extended

    val eventLog: EventLogClient = new EventLogClient {

      override def getEventByHash(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupResult] = {
        wasHere1.set(true)
        assert(wasHere1.get())
        assert(queryDepth == UpperLower)
        assert(responseForm == _responseForm)
        assert(blockchainInfo == _blockchainInfo)
        Future.successful(LookupResult.Found(HashHelper.bytesToPrintableId(hash), Payload, uppWithChain, anchors))
      }

      override def getEventBySignature(sig: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupResult] = {
        wasHere2.set(true)
        assert(wasHere2.get())
        assert(queryDepth == Simple)
        assert(responseForm == _responseForm)
        assert(blockchainInfo == _blockchainInfo)
        Future.successful(LookupResult.Found(HashHelper.bytesToPrintableId(sig), Signature, uppWithChain, anchors))
      }
    }

    val config = ConfigFactory.load().withValue("verification.health-check.port", ConfigValueFactory.fromAnyRef(PortGiver.giveMeHealthCheckPort))
    val redisCache = new RedisCache("test", config)
    val healthCheck = new HealthCheckProvider(config).get()
    val tokenPublicKey = new DefaultTokenPublicKey(config)
    val tokenVerification = new DefaultTokenVerification(config, tokenPublicKey)
    val lifecycle = new DefaultLifecycle()
    val acct = new DefaultAcctEventPublishing(config, lifecycle)
    val api = new DefaultApi(acct, tokenVerification, eventLog, new KeyServiceBasedVerifier(keyService), redisCache, healthCheck)

    Await.result(api.verifyUPPWithUpperAndLowerBoundV2("c29tZSBieXRlcyEAAQIDnw==".getBytes(StandardCharsets.UTF_8), _responseForm.value, _blockchainInfo.value, authToken = aToken), 10.seconds)
    assert(wasHere1.get())
    assert(wasHere2.get())

  }

  it should "successfully pass parameters through when default are modified for verifyUPP" in {

    val wasHere1 = new AtomicBoolean(false)
    val wasHere2 = new AtomicBoolean(false)

    val eventLog: EventLogClient = new EventLogClient {

      override def getEventByHash(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupResult] = {
        wasHere1.set(true)
        assert(wasHere1.get())
        assert(queryDepth == Simple)
        assert(responseForm == AnchorsNoPath)
        assert(blockchainInfo == Normal)
        Future.successful(LookupResult.Found(HashHelper.bytesToPrintableId(hash), Payload, uppWithChain, anchors))
      }

      override def getEventBySignature(sig: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupResult] = {
        wasHere2.set(true)
        assert(wasHere2.get())
        assert(queryDepth == Simple)
        assert(responseForm == AnchorsNoPath)
        assert(blockchainInfo == Normal)
        Future.successful(LookupResult.Found(HashHelper.bytesToPrintableId(sig), Signature, uppWithChain, anchors))
      }
    }

    val config = ConfigFactory.load().withValue("verification.health-check.port", ConfigValueFactory.fromAnyRef(PortGiver.giveMeHealthCheckPort))
    val redisCache = new RedisCache("test", config)
    val healthCheck = new HealthCheckProvider(config).get()
    val tokenPublicKey = new DefaultTokenPublicKey(config)
    val tokenVerification = new DefaultTokenVerification(config, tokenPublicKey)
    val lifecycle = new DefaultLifecycle()
    val acct = new DefaultAcctEventPublishing(config, lifecycle)
    val api = new DefaultApi(acct, tokenVerification, eventLog, new KeyServiceBasedVerifier(keyService), redisCache, healthCheck)

    Await.result(api.verifyUPPV2("c29tZSBieXRlcyEAAQIDnw==".getBytes(StandardCharsets.UTF_8), authToken = aToken), 10.seconds)
    assert(wasHere1.get())
    assert(wasHere2.get())

  }

  it should "successfully pass parameters through when default are modified for getUPP2" in {

    val wasHere = new AtomicBoolean(false)

    val eventLog: EventLogClient = new EventLogClient {

      override def getEventByHash(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupResult] = {
        wasHere.set(true)
        assert(wasHere.get())
        assert(queryDepth == Simple)
        assert(responseForm == AnchorsNoPath)
        assert(blockchainInfo == Normal)
        Future.successful(LookupResult.Found(HashHelper.bytesToPrintableId(hash), Payload, upp, anchors))
      }

      override def getEventBySignature(sig: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupResult] = {
        Future.successful(LookupResult.Found(HashHelper.bytesToPrintableId(sig), Signature, uppWithChain, anchors))
      }
    }

    val config = ConfigFactory.load().withValue("verification.health-check.port", ConfigValueFactory.fromAnyRef(PortGiver.giveMeHealthCheckPort))
    val redisCache = new RedisCache("test", config)
    val healthCheck = new HealthCheckProvider(config).get()
    val tokenPublicKey = new DefaultTokenPublicKey(config)
    val tokenVerification = new DefaultTokenVerification(config, tokenPublicKey)
    val lifecycle = new DefaultLifecycle()
    val acct = new DefaultAcctEventPublishing(config, lifecycle)
    val api = new DefaultApi(acct, tokenVerification, eventLog, new KeyServiceBasedVerifier(keyService), redisCache, healthCheck)

    Await.result(api.getUPPV2("c29tZSBieXRlcyEAAQIDnw==".getBytes(StandardCharsets.UTF_8), authToken = aToken), 10.seconds)

    assert(wasHere.get())

  }

  it should "successfully pass parameters through when default are modified for getUPP2 with evt publishing same UUIDs" in {

    val wasHere = new AtomicBoolean(false)
    val wasHereAsWell = new AtomicBoolean(false)

    val eventLog: EventLogClient = new EventLogClient {

      override def getEventByHash(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupResult] = {
        wasHere.set(true)
        assert(wasHere.get())
        assert(queryDepth == Simple)
        assert(responseForm == AnchorsNoPath)
        assert(blockchainInfo == Normal)
        Future.successful(LookupResult.Found(HashHelper.bytesToPrintableId(hash), Payload, upp, anchors))
      }

      override def getEventBySignature(sig: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupResult] = {
        Future.successful(LookupResult.Found(HashHelper.bytesToPrintableId(sig), Signature, uppWithChain, anchors))
      }
    }

    val config = ConfigFactory.load().withValue("verification.health-check.port", ConfigValueFactory.fromAnyRef(PortGiver.giveMeHealthCheckPort))
    val redisCache = new RedisCache("test", config)
    val healthCheck = new HealthCheckProvider(config).get()
    val tokenPublicKey = new DefaultTokenPublicKey(config)
    val tokenVerification = new DefaultTokenVerification(config, tokenPublicKey)
    val lifecycle = new DefaultLifecycle()
    val acct = new DefaultAcctEventPublishing(config, lifecycle) {
      override def publish(value: AcctEvent): Task[RecordMetadata] = {

        wasHereAsWell.set(true)
        assert(wasHereAsWell.get())

        Task(new RecordMetadata(
          new TopicPartition("topic", 1),
          1,
          1,
          new Date().getTime,
          1L,
          1,
          1
        ))
      }
    }
    val api = new DefaultApi(acct, tokenVerification, eventLog, new KeyServiceBasedVerifier(keyService), redisCache, healthCheck)

    Await.result(api.getUPPV2("c29tZSBieXRlcyEAAQIDnw==".getBytes(StandardCharsets.UTF_8), authToken = aToken), 10.seconds)

    assert(wasHere.get())
    assert(wasHereAsWell.get())

  }

  it should "successfully pass parameters through when default are modified for getUPP2 when not found" in {

    val wasHere = new AtomicBoolean(false)
    val wasHereAsWell = new AtomicBoolean(false)

    val eventLog: EventLogClient = new EventLogClient {

      override def getEventByHash(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupResult] = {
        wasHere.set(true)
        assert(wasHere.get())
        assert(queryDepth == Simple)
        assert(responseForm == AnchorsNoPath)
        assert(blockchainInfo == Normal)
        Future.successful(LookupResult.NotFound(HashHelper.bytesToPrintableId(hash), Payload))
      }

      override def getEventBySignature(sig: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupResult] = {
        Future.successful(LookupResult.Found(HashHelper.bytesToPrintableId(sig), Signature, uppWithChain, anchors))
      }
    }

    val config = ConfigFactory.load().withValue("verification.health-check.port", ConfigValueFactory.fromAnyRef(PortGiver.giveMeHealthCheckPort))
    val redisCache = new RedisCache("test", config)
    val healthCheck = new HealthCheckProvider(config).get()
    val tokenPublicKey = new DefaultTokenPublicKey(config)
    val tokenVerification = new DefaultTokenVerification(config, tokenPublicKey)
    val lifecycle = new DefaultLifecycle()
    val acct = new DefaultAcctEventPublishing(config, lifecycle) {
      override def publish(value: AcctEvent): Task[RecordMetadata] = {

        wasHereAsWell.set(true)
        assert(wasHereAsWell.get())

        Task(new RecordMetadata(
          new TopicPartition("topic", 1),
          1,
          1,
          new Date().getTime,
          1L,
          1,
          1
        ))
      }
    }
    val api = new DefaultApi(acct, tokenVerification, eventLog, new KeyServiceBasedVerifier(keyService), redisCache, healthCheck)

    Await.result(api.getUPPV2("aaaaaaaa".getBytes(StandardCharsets.UTF_8), authToken = aToken), 10.seconds)

    assert(wasHere.get())
    assert(!wasHereAsWell.get())

  }

  val redis = new RedisServer()

  override def beforeAll(): Unit = redis.start()

  override def afterAll(): Unit = redis.stop()

  override protected def beforeEach(): Unit = {
    CollectorRegistry.defaultRegistry.clear()
  }
}

