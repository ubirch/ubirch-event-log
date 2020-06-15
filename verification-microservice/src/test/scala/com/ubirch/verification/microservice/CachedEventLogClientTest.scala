package com.ubirch.verification.microservice

import com.typesafe.config.ConfigFactory
import com.ubirch.niomon.cache.RedisCache
import com.ubirch.verification.microservice.eventlog._
import com.ubirch.verification.microservice.models._
import org.scalatest.{AsyncFlatSpec, BeforeAndAfterAll, Matchers}
import redis.embedded.RedisServer

import scala.concurrent.Future

class CachedEventLogClientTest extends AsyncFlatSpec with Matchers with BeforeAndAfterAll {
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

  "CachedEventLogClient" should "use redis to cache calls" in {
    var underlyingClientInvocations = 0
    val underlying: EventLogClient = new EventLogClient {
      val x: EventLogClient.Response = EventLogClient.Response.fromJson(testResponse(success = true, message = null, data = testData))

      override def getEventByHash(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[EventLogClient.Response] = {
        underlyingClientInvocations += 1
        Future.successful(x)
      }

      override def getEventBySignature(sig: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[EventLogClient.Response] = {
        underlyingClientInvocations += 1
        Future.successful(x)
      }
    }

    val redisCache = new RedisCache("test", ConfigFactory.load())
    val cachedClient = new CachedEventLogClient(underlying, redisCache)

    for {
      res1 <- cachedClient.getEventByHash(Array(1), Simple, AnchorsNoPath, Normal)
      _ = underlyingClientInvocations should equal(1)
      res2 <- cachedClient.getEventByHash(Array(1), Simple, AnchorsNoPath, Normal)
      _ = underlyingClientInvocations should equal(1)
      _ = res1.toString should equal(res2.toString) // something down there doesn't implement equals properly
      _ <- cachedClient.getEventByHash(Array(2), Simple, AnchorsNoPath, Normal)
    } yield underlyingClientInvocations should equal(2)
  }

  val redis = new RedisServer()

  override def beforeAll(): Unit = redis.start()

  override def afterAll(): Unit = redis.stop()
}
