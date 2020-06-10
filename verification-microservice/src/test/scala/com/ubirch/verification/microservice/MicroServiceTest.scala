package com.ubirch.verification.microservice

import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.{Base64, UUID}

import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import com.ubirch.client.util.curveFromString
import com.ubirch.crypto.{GeneratorKeyFactory, PubKey}
import com.ubirch.niomon.cache.RedisCache
import com.ubirch.verification.microservice.Api.{Anchors, Failure, NotFound, Success}
import com.ubirch.verification.microservice.Main._
import com.ubirch.verification.microservice.eventlog._
import io.udash.rest.raw.JsonValue
import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import redis.embedded.RedisServer

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class MicroServiceTest extends FlatSpec with Matchers with BeforeAndAfterAll {

  val anchors =
    """[
    {
      "status": "added",
      "txid": "51f6cfe400bd1062f8fcde5dc5c23aaac111e8124886ecf1f60c33015a35ccb0",
      "message": "e392457bdd63db37d00435bfdc0a0a7f4a85f3664b9439956a4f4f2310fd934df85ea4a02823d4674c891f224bcab8c8f2c117fdc8710ce78c928fc9de8d9e19",
      "blockchain": "ethereum",
      "network_info": "Rinkeby Testnet Network",
      "network_type": "testnet",
      "created": "2019-05-07T21:30:14.421095"
    }
  ]"""
  val testData =
    s"""{
    "key": "key",
    "query_type": "payload",
    "event": {
      "hint": 0,
      "payload": "c29tZSBieXRlcyEAAQIDnw==",
      "signature": "5aTelLQBerVT/vJiL2qjZCxWxqlfwT/BaID0zUVy7LyUC9nUdb02//aCiZ7xH1HglDqZ0Qqb7GyzF4jtBxfSBg==",
      "signed": "lRKwjni1ymWXEeiBhcg+pwAOTQCwc29tZSBieXRlcyEAAQIDnw==",
      "uuid": "8e78b5ca-6597-11e8-8185-c83ea7000e4d",
      "version": 34
    },
    "anchors": $anchors
  }"""

  val testDataWithChain =
    s"""{
    "key": "key",
    "query_type": "payload",
    "event": {
      "hint": 0,
      "payload": "c29tZSBieXRlcyEAAQIDnw==",
      "signature": "5aTelLQBerVT/vJiL2qjZCxWxqlfwT/BaID0zUVy7LyUC9nUdb02//aCiZ7xH1HglDqZ0Qqb7GyzF4jtBxfSBg==",
      "signed": "lRKwjni1ymWXEeiBhcg+pwAOTQCwc29tZSBieXRlcyEAAQIDnw==",
      "uuid": "8e78b5ca-6597-11e8-8185-c83ea7000e4d",
      "version": 34,
      "chain": "lRKwjni1ymWXEeiBhcg+pwAOTQCwc29tZSBieXRlcyEAAQIDnw=="
    },
    "anchors": $anchors
  }"""

  def testResponse(success: Boolean, message: String, data: String) =
    s"""{
    "success": $success,
    "message": $message,
    "data": $data
  }"""

  val cert: PubKey = GeneratorKeyFactory.getPubKey(
    Base64.getDecoder.decode("l/KJeVnO8xTXkW7bjf+OumE7vXxBIkPHg85/uVAbBiY="),
    curveFromString("ECC_ED25519")
  )


  val keyServiceConfig: Config = ConfigFactory.empty()
    .withValue("ubirchKeyService.client.rest.host", ConfigValueFactory.fromAnyRef("abcd"))
  val keyService: KeyServerClient = new KeyServerClient(keyServiceConfig) {
    override def getPublicKey(uuid: UUID): List[PubKey] = List(cert)
  }

  "ApiImpl" should "successfully validate handle a valid packet" in {
    val eventLog: EventLogClient = new EventLogClient {
      val x: EventLogClient.Response = EventLogClient.Response.fromJson(testResponse(success = true, message = null, data = testData))

      override def getEventByHash(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[EventLogClient.Response] =
        Future.successful(x)

      override def getEventBySignature(sig: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[EventLogClient.Response] =
        Future.successful(x)
    }

    val config = ConfigFactory.load()
    val redisCache = new RedisCache("test", config)
    val healthcheck = Main.initHealthchecks(config.getConfig("verification"))
    val api = new ApiImpl(eventLog, new KeyServiceBasedVerifier(keyService), redisCache, healthcheck)

    val res = Await.result(api.verifyUPPWithUpperBound("c29tZSBieXRlcyEAAQIDnw==".getBytes(StandardCharsets.UTF_8)), 10.seconds)

    res should equal(Success(
      "lRKwjni1ymWXEeiBhcg+pwAOTQCwc29tZSBieXRlcyEAAQIDn8RA5aTelLQBerVT/vJiL2qjZCxWxqlfwT/BaID0zUVy7LyUC9nUdb02//aCiZ7xH1HglDqZ0Qqb7GyzF4jtBxfSBg==",
      null,
      Anchors(JsonValue(anchors)))
    )
  }

  it should "return an error when handling an invalid packet" in {
    val eventLog: EventLogClient = new EventLogClient {
      val mapper = new ObjectMapper
      mapper.configure(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS, true)

      val x: EventLogClient.Response = EventLogClient.Response.fromJson(testResponse(success = true, message = null, data = testData))
      x.data.event.getSignature()(0) = (x.data.event.getSignature()(0) + 1).toByte

      override def getEventByHash(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[EventLogClient.Response] =
        Future.successful(x)

      override def getEventBySignature(sig: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[EventLogClient.Response] =
        Future.successful(x)
    }

    val config = ConfigFactory.load().withValue("verification.health-check.port", ConfigValueFactory.fromAnyRef(PortGiver.giveMeHealthCheckPort))
    val redisCache = new RedisCache("test", config)
    val healthcheck = initHealthchecks(config.getConfig("verification"))
    val api = new ApiImpl(eventLog, new KeyServiceBasedVerifier(keyService), redisCache, healthcheck)

    val res = Await.result(api.verifyUPPWithUpperBound("c29tZSBieXRlcyEAAQIDnw==".getBytes(StandardCharsets.UTF_8)), 10.seconds)

    res should equal(Failure())
  }

  it should "return NotFound error when handling non-existent packet" in {
    val eventLog: EventLogClient = new EventLogClient {
      override def getEventByHash(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[EventLogClient.Response] = Future.successful(null)

      override def getEventBySignature(signature: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[EventLogClient.Response] = Future.successful(null)
    }

    val config = ConfigFactory.load().withValue("verification.health-check.port", ConfigValueFactory.fromAnyRef(PortGiver.giveMeHealthCheckPort))
    val redisCache = new RedisCache("test", config)
    val healthcheck = initHealthchecks(config.getConfig("verification"))
    val api = new ApiImpl(eventLog, new KeyServiceBasedVerifier(keyService), redisCache, healthcheck)

    val res = Await.result(api.verifyUPP("c29tZSBieXRlcyEAAQIDnw==".getBytes(StandardCharsets.UTF_8)), 10.seconds)

    res should equal(NotFound)
  }

  it should "return a Failure(EventLogError) if there's any errors from event log" in {
    val eventLog: EventLogClient = new EventLogClient {
      val res: Future[EventLogClient.Response] = Future.successful(EventLogClient.Response(
        success = false, message = "plutonium leakage", data = null
      ))

      override def getEventByHash(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[EventLogClient.Response] = res

      override def getEventBySignature(signature: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[EventLogClient.Response] = res
    }

    val config = ConfigFactory.load().withValue("verification.health-check.port", ConfigValueFactory.fromAnyRef(PortGiver.giveMeHealthCheckPort))
    val redisCache = new RedisCache("test", config)
    val healthcheck = initHealthchecks(config.getConfig("verification"))
    val api = new ApiImpl(eventLog, new KeyServiceBasedVerifier(keyService), redisCache, healthcheck)

    val res = Await.result(api.verifyUPPWithUpperBound("c29tZSBieXRlcyEAAQIDnw==".getBytes(StandardCharsets.UTF_8)), 10.seconds)

    res should equal(Failure(errorType = "EventLogError", errorMessage = "plutonium leakage"))
  }

  it should "successfully pass parameters through when default for verifyUPPWithUpperBound" in {

    val wasHere1 = new AtomicBoolean(false)
    val wasHere2 = new AtomicBoolean(false)

    val eventLog: EventLogClient = new EventLogClient {
      val x: EventLogClient.Response = EventLogClient.Response.fromJson(testResponse(success = true, message = null, data = testDataWithChain))

      override def getEventByHash(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[EventLogClient.Response] = {
        wasHere1.set(true)
        assert(wasHere1.get())
        assert(queryDepth == ShortestPath)
        assert(responseForm == AnchorsNoPath)
        assert(blockchainInfo == Normal)
        Future.successful(x)
      }

      override def getEventBySignature(sig: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[EventLogClient.Response] = {
        wasHere2.set(true)
        assert(wasHere2.get())
        assert(queryDepth == Simple)
        assert(responseForm == AnchorsNoPath)
        assert(blockchainInfo == Normal)
        Future.successful(x)
      }
    }

    val config = ConfigFactory.load().withValue("verification.health-check.port", ConfigValueFactory.fromAnyRef(PortGiver.giveMeHealthCheckPort))
    val redisCache = new RedisCache("test", config)
    val healthcheck = initHealthchecks(config.getConfig("verification"))
    val api = new ApiImpl(eventLog, new KeyServiceBasedVerifier(keyService), redisCache, healthcheck)

    Await.result(api.verifyUPPWithUpperBound("c29tZSBieXRlcyEAAQIDnw==".getBytes(StandardCharsets.UTF_8)), 10.seconds)

    assert(wasHere1.get())
    assert(wasHere2.get())

  }

  it should "successfully pass parameters through when default for verifyUPPWithUpperAndLowerBound" in {

    val wasHere1 = new AtomicBoolean(false)
    val wasHere2 = new AtomicBoolean(false)

    val eventLog: EventLogClient = new EventLogClient {
      val x: EventLogClient.Response = EventLogClient.Response.fromJson(testResponse(success = true, message = null, data = testDataWithChain))

      override def getEventByHash(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[EventLogClient.Response] = {
        wasHere1.set(true)
        assert(wasHere1.get())
        assert(queryDepth == UpperLower)
        assert(responseForm == AnchorsNoPath)
        assert(blockchainInfo == Normal)
        Future.successful(x)
      }

      override def getEventBySignature(sig: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[EventLogClient.Response] = {
        wasHere2.set(true)
        assert(wasHere2.get())
        assert(queryDepth == Simple)
        assert(responseForm == AnchorsNoPath)
        assert(blockchainInfo == Normal)
        Future.successful(x)
      }
    }

    val config = ConfigFactory.load().withValue("verification.health-check.port", ConfigValueFactory.fromAnyRef(PortGiver.giveMeHealthCheckPort))
    val redisCache = new RedisCache("test", config)
    val healthcheck = initHealthchecks(config.getConfig("verification"))
    val api = new ApiImpl(eventLog, new KeyServiceBasedVerifier(keyService), redisCache, healthcheck)

    Await.result(api.verifyUPPWithUpperAndLowerBound("c29tZSBieXRlcyEAAQIDnw==".getBytes(StandardCharsets.UTF_8)), 10.seconds)
    assert(wasHere1.get())
    assert(wasHere2.get())

  }

  it should "successfully pass parameters through when default are modified for verifyUPPWithUpperBound" in {

    val wasHere1 = new AtomicBoolean(false)
    val wasHere2 = new AtomicBoolean(false)
    val _responseForm = AnchorsWithPath
    val _blockchainInfo = Extended

    val eventLog: EventLogClient = new EventLogClient {
      val x: EventLogClient.Response = EventLogClient.Response.fromJson(testResponse(success = true, message = null, data = testDataWithChain))

      override def getEventByHash(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[EventLogClient.Response] = {
        wasHere1.set(true)
        assert(wasHere1.get())
        assert(queryDepth == ShortestPath)
        assert(responseForm == _responseForm)
        assert(blockchainInfo == _blockchainInfo)
        Future.successful(x)
      }

      override def getEventBySignature(sig: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[EventLogClient.Response] = {
        wasHere2.set(true)
        assert(wasHere2.get())
        assert(queryDepth == Simple)
        assert(responseForm == _responseForm)
        assert(blockchainInfo == _blockchainInfo)
        Future.successful(x)
      }
    }

    val config = ConfigFactory.load().withValue("verification.health-check.port", ConfigValueFactory.fromAnyRef(PortGiver.giveMeHealthCheckPort))
    val redisCache = new RedisCache("test", config)
    val healthcheck = initHealthchecks(config.getConfig("verification"))
    val api = new ApiImpl(eventLog, new KeyServiceBasedVerifier(keyService), redisCache, healthcheck)

    Await.result(api.verifyUPPWithUpperBound("c29tZSBieXRlcyEAAQIDnw==".getBytes(StandardCharsets.UTF_8), _responseForm.value, _blockchainInfo.value), 10.seconds)
    assert(wasHere1.get())
    assert(wasHere2.get())

  }

  it should "successfully pass parameters through when default are modified for verifyUPPWithUpperAndLowerBound" in {

    val wasHere1 = new AtomicBoolean(false)
    val wasHere2 = new AtomicBoolean(false)

    val _responseForm = AnchorsWithPath
    val _blockchainInfo = Extended

    val eventLog: EventLogClient = new EventLogClient {
      val x: EventLogClient.Response = EventLogClient.Response.fromJson(testResponse(success = true, message = null, data = testDataWithChain))

      override def getEventByHash(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[EventLogClient.Response] = {
        wasHere1.set(true)
        assert(wasHere1.get())
        assert(queryDepth == UpperLower)
        assert(responseForm == _responseForm)
        assert(blockchainInfo == _blockchainInfo)
        Future.successful(x)
      }

      override def getEventBySignature(sig: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[EventLogClient.Response] = {
        wasHere2.set(true)
        assert(wasHere2.get())
        assert(queryDepth == Simple)
        assert(responseForm == _responseForm)
        assert(blockchainInfo == _blockchainInfo)
        Future.successful(x)
      }
    }

    val config = ConfigFactory.load().withValue("verification.health-check.port", ConfigValueFactory.fromAnyRef(PortGiver.giveMeHealthCheckPort))
    val redisCache = new RedisCache("test", config)
    val healthcheck = initHealthchecks(config.getConfig("verification"))
    val api = new ApiImpl(eventLog, new KeyServiceBasedVerifier(keyService), redisCache, healthcheck)

    Await.result(api.verifyUPPWithUpperAndLowerBound("c29tZSBieXRlcyEAAQIDnw==".getBytes(StandardCharsets.UTF_8), _responseForm.value, _blockchainInfo.value), 10.seconds)
    assert(wasHere1.get())
    assert(wasHere2.get())

  }

  it should "successfully pass parameters through when default are modified for verifyUPP" in {

    val wasHere1 = new AtomicBoolean(false)
    val wasHere2 = new AtomicBoolean(false)

    val eventLog: EventLogClient = new EventLogClient {
      val x: EventLogClient.Response = EventLogClient.Response.fromJson(testResponse(success = true, message = null, data = testDataWithChain))

      override def getEventByHash(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[EventLogClient.Response] = {
        wasHere1.set(true)
        assert(wasHere1.get())
        assert(queryDepth == Simple)
        assert(responseForm == AnchorsNoPath)
        assert(blockchainInfo == Normal)
        Future.successful(x)
      }

      override def getEventBySignature(sig: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[EventLogClient.Response] = {
        wasHere2.set(true)
        assert(wasHere2.get())
        assert(queryDepth == Simple)
        assert(responseForm == AnchorsNoPath)
        assert(blockchainInfo == Normal)
        Future.successful(x)
      }
    }

    val config = ConfigFactory.load().withValue("verification.health-check.port", ConfigValueFactory.fromAnyRef(PortGiver.giveMeHealthCheckPort))
    val redisCache = new RedisCache("test", config)
    val healthcheck = initHealthchecks(config.getConfig("verification"))
    val api = new ApiImpl(eventLog, new KeyServiceBasedVerifier(keyService), redisCache, healthcheck)

    Await.result(api.verifyUPP("c29tZSBieXRlcyEAAQIDnw==".getBytes(StandardCharsets.UTF_8)), 10.seconds)
    assert(wasHere1.get())
    assert(wasHere2.get())


  }

  it should "successfully pass parameters through when default are modified for getUPP" in {

    val wasHere = new AtomicBoolean(false)

    val eventLog: EventLogClient = new EventLogClient {
      val x: EventLogClient.Response = EventLogClient.Response.fromJson(testResponse(success = true, message = null, data = testData))

      override def getEventByHash(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[EventLogClient.Response] = {
        wasHere.set(true)
        assert(wasHere.get())
        assert(queryDepth == Simple)
        assert(responseForm == AnchorsNoPath)
        assert(blockchainInfo == Normal)
        Future.successful(x)
      }

      override def getEventBySignature(sig: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[EventLogClient.Response] = {
        Future.successful(x)
      }
    }

    val config = ConfigFactory.load().withValue("verification.health-check.port", ConfigValueFactory.fromAnyRef(PortGiver.giveMeHealthCheckPort))
    val redisCache = new RedisCache("test", config)
    val healthcheck = initHealthchecks(config.getConfig("verification"))
    val api = new ApiImpl(eventLog, new KeyServiceBasedVerifier(keyService), redisCache, healthcheck)

    Await.result(api.getUPP("c29tZSBieXRlcyEAAQIDnw==".getBytes(StandardCharsets.UTF_8)), 10.seconds)
    assert(wasHere.get())

  }

  val redis = new RedisServer()

  override def beforeAll(): Unit = redis.start()

  override def afterAll(): Unit = redis.stop()
}
