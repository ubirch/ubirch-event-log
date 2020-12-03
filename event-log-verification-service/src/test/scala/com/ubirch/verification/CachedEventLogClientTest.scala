package com.ubirch.verification

import com.typesafe.config.{ ConfigFactory, ConfigValueFactory }
import com.ubirch.services.lifeCycle.DefaultLifecycle
import com.ubirch.verification.models._
import com.ubirch.verification.services.RedisProvider
import com.ubirch.verification.services.eventlog.{ CachedEventLogClient, EventLogClient }
import com.ubirch.verification.util.HashHelper
import org.json4s.JsonAST.JString
import org.scalatest.{ AsyncFlatSpec, BeforeAndAfterAll, Matchers }
import redis.embedded.RedisServer

import scala.concurrent.Future

class CachedEventLogClientTest extends AsyncFlatSpec with Matchers with BeforeAndAfterAll {

  "CachedEventLogClient" should "use redis to cache calls" in {
    var underlyingClientInvocations = 0
    val underlying: EventLogClient = new EventLogClient {

      override def getEventByHash(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupResult] = {
        underlyingClientInvocations += 1
        Future.successful(LookupResult.Found(value = HashHelper.bytesToPrintableId(hash), Payload, JString("event by hash"), JString("anchors by hash")))
      }

      override def getEventBySignature(signature: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupResult] = {
        underlyingClientInvocations += 1
        Future.successful(LookupResult.Found(value = HashHelper.bytesToPrintableId(signature), Payload, JString("event by signature"), JString("anchors by signature")))
      }
    }

    val config = ConfigFactory.load().withValue("verification.health-check.port", ConfigValueFactory.fromAnyRef(PortGiver.giveMeHealthCheckPort))
    val lifecycle = new DefaultLifecycle()
    val redisCache = new RedisProvider(config, lifecycle).get()
    val cachedClient = new CachedEventLogClient(underlying, redisCache)

    for {
      res1 <- cachedClient.getEventByHash(Array(1), Simple, AnchorsNoPath, Normal)
      _ = underlyingClientInvocations should equal(1)
      res2 <- cachedClient.getEventByHash(Array(1), Simple, AnchorsNoPath, Normal)
      _ = underlyingClientInvocations should equal(1)
      _ = res1 should equal(res2)
      _ <- cachedClient.getEventByHash(Array(2), Simple, AnchorsNoPath, Normal)
    } yield underlyingClientInvocations should equal(2)
  }

  val redis: RedisServer = new RedisServer()

  override def beforeAll(): Unit = redis.start()

  override def afterAll(): Unit = redis.stop()

}
