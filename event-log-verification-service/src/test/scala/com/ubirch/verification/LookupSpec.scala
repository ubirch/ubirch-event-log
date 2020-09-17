package com.ubirch.verification

import java.util.UUID

import com.github.nosan.embedded.cassandra.cql.CqlScript
import com.google.inject.Module
import com.google.inject.binder.ScopedBindingBuilder
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models._
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.util._
import com.ubirch.verification.models._
import com.ubirch.verification.services.eventlog.EventLogClient
import com.ubirch.verification.services.{ CassandraFinder, Finder }
import com.ubirch.verification.services.janus.{ DefaultTestingGremlinConnector, Gremlin }
import com.ubirch.verification.util.LookupJsonSupport
import io.prometheus.client.CollectorRegistry
import javax.inject._
import org.json4s.jackson.JsonMethods.parse
import org.json4s.{ JNull, JValue }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps

class FakeEmptyFinder @Inject() (cassandraFinder: CassandraFinder)(implicit val ec: ExecutionContext) extends Finder {

  def findEventLog(value: String, category: String): Future[Option[EventLogRow]] = cassandraFinder.findEventLog(value, category)

  def findByPayload(value: String): Future[Option[EventLogRow]] = findEventLog(value, Values.UPP_CATEGORY)

  def findBySignature(value: String): Future[Option[EventLogRow]] = Future.successful(None)

  def findAnchorsWithPathAsVertices(id: String): Future[(List[VertexStruct], List[VertexStruct])] = Future.successful((Nil, Nil))

  def findUpperAndLowerAsVertices(id: String): Future[(List[VertexStruct], List[VertexStruct], List[VertexStruct], List[VertexStruct])] = Future.successful((Nil, Nil, Nil, Nil))

}

class FakeFoundFinder @Inject() (cassandraFinder: CassandraFinder)(implicit val ec: ExecutionContext) extends Finder {

  def findEventLog(value: String, category: String): Future[Option[EventLogRow]] = cassandraFinder.findEventLog(value, category)

  def findByPayload(value: String): Future[Option[EventLogRow]] = findEventLog(value, Values.UPP_CATEGORY)

  //TODO: We need to find a way to mock gremlin with asynchronous calls.
  def findBySignature(value: String): Future[Option[EventLogRow]] = cassandraFinder.findUPP(value, Signature)

  def findAnchorsWithPathAsVertices(id: String): Future[(List[VertexStruct], List[VertexStruct])] =
    Future.successful((FakeFoundFinder.simplePath, FakeFoundFinder.blockchains))

  def findUpperAndLowerAsVertices(id: String): Future[(List[VertexStruct], List[VertexStruct], List[VertexStruct], List[VertexStruct])] =
    Future.successful((FakeFoundFinder.upperPath, FakeFoundFinder.upperBlockchains, FakeFoundFinder.lowerPath, FakeFoundFinder.lowerBlockchains))

}

object FakeFoundFinder {

  val upperPath = List(
    VertexStruct("1", "UPP", properties = Map(
      "next_hash" -> "eUhr08+42ZL5KTwV6+QrbZ+HLGKgxGLHONd9WuZ6bt/wf/hBdMxHjPa+3Kb8aUC9yvhGHotGXZrvQZ2wpSe2HQ==",
      "signature" -> "bCWe6UOwYCJlZ5nEQmiQrqzW7PwMl2DSi1loPNwMmukD9lnTm7xACePNP4BzzWt3NSvqTqC/Nqka/GBDVXDZAg==",
      "hash" -> "/gQVsIcokNP8DF9J8dAz7u7QxMzCODjmZLWIyCI93Zw8j6WQsy9QTX2HgpRL5S3nuO40vldfvWERLiE3axJiXQ==",
      "prev_hash" -> "/gQVsIcokNP8DF9J8dAz7u7QxMzCODjmZLWIyCI93Zw8j6WQsy9QTX2HgpRL5S3nuO40vldfvWERLiE3axJiXQ==",
      "type" -> "UPP"
    )),
    VertexStruct("2", "UPP", properties = Map(
      "next_hash" -> "18f8491f333b8ecf1bdb34db094284cb9416acc1ad04654617a05cdbb7686862f52f9963a681d11bb76e5bce680483f15f6916736855827bcf5fb11424d91fc6",
      "signature" -> "YTKC9pYsKHaaxoz4g6r6MXgHq96eodAZWG5HaYHkPDX4hubgVtry36pypJORTGsYGujAfgtkhFyP1yYjdZZgDg==",
      "hash" -> "eUhr08+42ZL5KTwV6+QrbZ+HLGKgxGLHONd9WuZ6bt/wf/hBdMxHjPa+3Kb8aUC9yvhGHotGXZrvQZ2wpSe2HQ==",
      "prev_hash" -> "",
      "type" -> "UPP"
    )),
    VertexStruct("3", "SLAVE_TREE", properties = Map(
      "next_hash" -> "ec0a1fbd0b5be6dd8ef1095293053e4d62b7348f37ed29beede57066900de3e38d1d3de8769bc0d1b29f8f4593dcdb97e1bcf7b8b3b8227542b826fe993634a6",
      "hash" -> "18f8491f333b8ecf1bdb34db094284cb9416acc1ad04654617a05cdbb7686862f52f9963a681d11bb76e5bce680483f15f6916736855827bcf5fb11424d91fc6",
      "prev_hash" -> "eUhr08+42ZL5KTwV6+QrbZ+HLGKgxGLHONd9WuZ6bt/wf/hBdMxHjPa+3Kb8aUC9yvhGHotGXZrvQZ2wpSe2HQ==",
      "type" -> "SLAVE_TREE"
    )),
    VertexStruct("4", "MASTER_TREE", properties = Map(
      "next_hash" -> "Y9JAJGOZGLUQQFJOLEFVFUMTQILTZ9IKRPCFNEAGQEPRZPOWERJAQUQDCXHEUOCICGCSYCUBWDKBZ9999",
      "hash" -> "ec0a1fbd0b5be6dd8ef1095293053e4d62b7348f37ed29beede57066900de3e38d1d3de8769bc0d1b29f8f4593dcdb97e1bcf7b8b3b8227542b826fe993634a6",
      "prev_hash" -> "18f8491f333b8ecf1bdb34db094284cb9416acc1ad04654617a05cdbb7686862f52f9963a681d11bb76e5bce680483f15f6916736855827bcf5fb11424d91fc6",
      "type" -> "MASTER_TREE"
    ))
  )

  val upperBlockchains = List(
    VertexStruct("5", "PUBLIC_CHAIN", properties = Map(
      "public_chain" -> "IOTA_TESTNET_IOTA_TESTNET_NETWORK",
      "hash" -> "Y9JAJGOZGLUQQFJOLEFVFUMTQILTZ9IKRPCFNEAGQEPRZPOWERJAQUQDCXHEUOCICGCSYCUBWDKBZ9999",
      "prev_hash" -> "ec0a1fbd0b5be6dd8ef1095293053e4d62b7348f37ed29beede57066900de3e38d1d3de8769bc0d1b29f8f4593dcdb97e1bcf7b8b3b8227542b826fe993634a6",
      "type" -> "PUBLIC_CHAIN"
    ))
  )

  val lowerPath = List(
    VertexStruct(
      "6",
      "MASTER_TREE",
      properties = Map(
        "next_hash" -> "375f8683023272f4b31afd134411daddee94212d1f70f376c15ae23979207f26ece737f7ede575dd6b0b088cb154eb25a1ba09d35e51cf05b9bceb3e5f4e765e",
        "hash" -> "0a4303cf15ade07b6987dd31aba5cf4a5e60058644176c22122e91e7b94e90e59ccd362dfc88d3e84baa5eef9aa7b9dd0cdf6a6151dceb7a9459bd090925b233",
        "prev_hash" -> "",
        "type" -> "MASTER_TREE"
      )
    ),
    VertexStruct(
      "7",
      "MASTER_TREE",
      properties = Map(
        "next_hash" -> "ca96ca76c59753c27a100780d857bfdefe448ee16aa51aef6b3aa766bf28bb1e5bf355566124e830ef6641ef34f33a6b475641f4836dcbfe7b02c6edd6136eac",
        "hash" -> "375f8683023272f4b31afd134411daddee94212d1f70f376c15ae23979207f26ece737f7ede575dd6b0b088cb154eb25a1ba09d35e51cf05b9bceb3e5f4e765e",
        "prev_hash" -> "0a4303cf15ade07b6987dd31aba5cf4a5e60058644176c22122e91e7b94e90e59ccd362dfc88d3e84baa5eef9aa7b9dd0cdf6a6151dceb7a9459bd090925b233",
        "type" -> "MASTER_TREE"
      )
    ),
    VertexStruct(
      "8",
      "MASTER_TREE",
      properties = Map(
        "next_hash" -> "MBCRQSNGBLVYHMJBKJHGFKFIDKVTFJHSMWUFPSCNOCUUIKQNML9H9KYQWBXUKCKNZIIOSUXPDORQZ9999",
        "hash" -> "ca96ca76c59753c27a100780d857bfdefe448ee16aa51aef6b3aa766bf28bb1e5bf355566124e830ef6641ef34f33a6b475641f4836dcbfe7b02c6edd6136eac",
        "prev_hash" -> "375f8683023272f4b31afd134411daddee94212d1f70f376c15ae23979207f26ece737f7ede575dd6b0b088cb154eb25a1ba09d35e51cf05b9bceb3e5f4e765e",
        "type" -> "MASTER_TREE"
      )
    )
  )

  val lowerBlockchains = List(
    VertexStruct("9", "PUBLIC_CHAIN", properties = Map(
      "public_chain" -> "IOTA_TESTNET_IOTA_TESTNET_NETWORK",
      "hash" -> "MBCRQSNGBLVYHMJBKJHGFKFIDKVTFJHSMWUFPSCNOCUUIKQNML9H9KYQWBXUKCKNZIIOSUXPDORQZ9999",
      "prev_hash" -> "ca96ca76c59753c27a100780d857bfdefe448ee16aa51aef6b3aa766bf28bb1e5bf355566124e830ef6641ef34f33a6b475641f4836dcbfe7b02c6edd6136eac",
      "type" -> "PUBLIC_CHAIN"
    ))
  )

  val simplePath = List(
    VertexStruct("10", "UPP", properties = Map(
      "next_hash" -> "eUhr08+42ZL5KTwV6+QrbZ+HLGKgxGLHONd9WuZ6bt/wf/hBdMxHjPa+3Kb8aUC9yvhGHotGXZrvQZ2wpSe2HQ==",
      "signature" -> "bCWe6UOwYCJlZ5nEQmiQrqzW7PwMl2DSi1loPNwMmukD9lnTm7xACePNP4BzzWt3NSvqTqC/Nqka/GBDVXDZAg==",
      "hash" -> "/gQVsIcokNP8DF9J8dAz7u7QxMzCODjmZLWIyCI93Zw8j6WQsy9QTX2HgpRL5S3nuO40vldfvWERLiE3axJiXQ==",
      "prev_hash" -> "/gQVsIcokNP8DF9J8dAz7u7QxMzCODjmZLWIyCI93Zw8j6WQsy9QTX2HgpRL5S3nuO40vldfvWERLiE3axJiXQ==",
      "type" -> "UPP"
    )),
    VertexStruct("11", "UPP", properties = Map(
      "next_hash" -> "18f8491f333b8ecf1bdb34db094284cb9416acc1ad04654617a05cdbb7686862f52f9963a681d11bb76e5bce680483f15f6916736855827bcf5fb11424d91fc6",
      "signature" -> "YTKC9pYsKHaaxoz4g6r6MXgHq96eodAZWG5HaYHkPDX4hubgVtry36pypJORTGsYGujAfgtkhFyP1yYjdZZgDg==",
      "hash" -> "eUhr08+42ZL5KTwV6+QrbZ+HLGKgxGLHONd9WuZ6bt/wf/hBdMxHjPa+3Kb8aUC9yvhGHotGXZrvQZ2wpSe2HQ==",
      "prev_hash" -> "",
      "type" -> "UPP"
    )),
    VertexStruct("12", "SLAVE_TREE", properties = Map(
      "next_hash" -> "ec0a1fbd0b5be6dd8ef1095293053e4d62b7348f37ed29beede57066900de3e38d1d3de8769bc0d1b29f8f4593dcdb97e1bcf7b8b3b8227542b826fe993634a6",
      "hash" -> "18f8491f333b8ecf1bdb34db094284cb9416acc1ad04654617a05cdbb7686862f52f9963a681d11bb76e5bce680483f15f6916736855827bcf5fb11424d91fc6",
      "prev_hash" -> "eUhr08+42ZL5KTwV6+QrbZ+HLGKgxGLHONd9WuZ6bt/wf/hBdMxHjPa+3Kb8aUC9yvhGHotGXZrvQZ2wpSe2HQ==",
      "type" -> "SLAVE_TREE"
    )),
    VertexStruct("13", "MASTER_TREE", properties = Map(
      "next_hash" -> "Y9JAJGOZGLUQQFJOLEFVFUMTQILTZ9IKRPCFNEAGQEPRZPOWERJAQUQDCXHEUOCICGCSYCUBWDKBZ9999",
      "hash" -> "ec0a1fbd0b5be6dd8ef1095293053e4d62b7348f37ed29beede57066900de3e38d1d3de8769bc0d1b29f8f4593dcdb97e1bcf7b8b3b8227542b826fe993634a6",
      "prev_hash" -> "18f8491f333b8ecf1bdb34db094284cb9416acc1ad04654617a05cdbb7686862f52f9963a681d11bb76e5bce680483f15f6916736855827bcf5fb11424d91fc6",
      "type" -> "MASTER_TREE"
    ))
  )
  val blockchains = List(
    VertexStruct("14", "PUBLIC_CHAIN", properties = Map(
      "public_chain" -> "IOTA_TESTNET_IOTA_TESTNET_NETWORK",
      "hash" -> "Y9JAJGOZGLUQQFJOLEFVFUMTQILTZ9IKRPCFNEAGQEPRZPOWERJAQUQDCXHEUOCICGCSYCUBWDKBZ9999",
      "prev_hash" -> "ec0a1fbd0b5be6dd8ef1095293053e4d62b7348f37ed29beede57066900de3e38d1d3de8769bc0d1b29f8f4593dcdb97e1bcf7b8b3b8227542b826fe993634a6",
      "type" -> "PUBLIC_CHAIN"
    ))
  )
}

class LookupSpec extends TestBase with EmbeddedCassandra with LazyLogging {

  val insertEventSql: String =
    s"""
       |INSERT INTO events (id, customer_id, service_class, category, event, event_time, year, month, day, hour, minute, second, milli, signature, nonce)
       | VALUES ('c29tZSBieXRlcyEAAQIDnw==', 'customer_id', 'service_class', '${Values.UPP_CATEGORY}', '{
       |   "hint":0,
       |   "payload":"c29tZSBieXRlcyEAAQIDnw==",
       |   "signature":"5aTelLQBerVT/vJiL2qjZCxWxqlfwT/BaID0zUVy7LyUC9nUdb02//aCiZ7xH1HglDqZ0Qqb7GyzF4jtBxfSBg==",
       |   "signed":"lRKwjni1ymWXEeiBhcg+pwAOTQCwc29tZSBieXRlcyEAAQIDnw==",
       |   "uuid":"8e78b5ca-6597-11e8-8185-c83ea7000e4d",
       |   "version":34
       |}', '2019-01-29T17:00:28.333Z', 2019, 5, 2, 19, 439, 16, 0, '0681D35827B17104A2AACCE5A08C4CD1BC8A5EF5EFF4A471D15976693CC0D6D67392F1CACAE63565D6E521D2325A998CDE00A2FEF5B65D0707F4158000EB6D05',
       |'34376336396166392D336533382D343665652D393063332D616265313364383335353266');
    """.stripMargin

  val data: String =
    """
      |{
      |   "hint":0,
      |   "payload":"c29tZSBieXRlcyEAAQIDnw==",
      |   "signature":"5aTelLQBerVT/vJiL2qjZCxWxqlfwT/BaID0zUVy7LyUC9nUdb02//aCiZ7xH1HglDqZ0Qqb7GyzF4jtBxfSBg==",
      |   "signed":"lRKwjni1ymWXEeiBhcg+pwAOTQCwc29tZSBieXRlcyEAAQIDnw==",
      |   "uuid":"8e78b5ca-6597-11e8-8185-c83ea7000e4d",
      |   "version":34
      |}
    """.stripMargin

  val insertLookupSql: String =
    s"""INSERT INTO lookups (name, category, key, value) VALUES ('${Signature.value}', '${Values.UPP_CATEGORY}', 'c29tZSBieXRlcyEAAQIDnw==', '5aTelLQBerVT/vJiL2qjZCxWxqlfwT/BaID0zUVy7LyUC9nUdb02//aCiZ7xH1HglDqZ0Qqb7GyzF4jtBxfSBg==');""".stripMargin

  "Lookup Spec" must {

    "process successfully when Found" in {

      cassandra.executeScripts(CqlScript.statements(insertEventSql))

      val modules: List[Module] = List {
        new LookupServiceBinder {
          override def gremlin: ScopedBindingBuilder = bind(classOf[Gremlin]).to(classOf[DefaultTestingGremlinConnector])

          override def finder: ScopedBindingBuilder = bind(classOf[Finder]).to(classOf[FakeFoundFinder])
        }
      }

      val injector = new InjectorHelper(modules) {}
      val eventLogClient = injector.get[EventLogClient]

      val value = "c29tZSBieXRlcyEAAQIDnw=="
      val queryType = Payload
      val queryDepth = ShortestPath
      val responseForm = AnchorsWithPath
      val blockchainInfo = Normal

      val expectedResp = LookupResult.Found(
        value = value,
        queryType = queryType,
        event = LookupJsonSupport.getJValue(data),
        anchors = EventLogClient.shortestPathAsJValue(FakeFoundFinder.simplePath, FakeFoundFinder.blockchains)
      )

      val response = await(eventLogClient.getEventByHash(value.getBytes, queryDepth, responseForm, blockchainInfo), 5 seconds)

      assert(expectedResp.success == response.success)
      assert(expectedResp.message == response.message)
      assert(expectedResp.queryType == response.queryType)
      assert(expectedResp.anchors == response.anchors)
      assert(expectedResp.event == response.event)

    }

    "process successfully when NotFound" in {

      val modules: List[Module] = List {
        new LookupServiceBinder {

          override def gremlin: ScopedBindingBuilder = bind(classOf[Gremlin]).to(classOf[DefaultTestingGremlinConnector])

          override def finder: ScopedBindingBuilder = bind(classOf[Finder]).to(classOf[FakeEmptyFinder])

        }
      }

      val value = "c29tZSBieXRlcyEAAQIDnw=="
      val queryType = Payload

      val expectedResp = LookupResult.NotFound(
        value = value,
        queryType = queryType
      )

      val injector = new InjectorHelper(modules) {}
      val eventLogClient = injector.get[EventLogClient]

      val response = await(eventLogClient.getEventByHash(value.getBytes, Simple, AnchorsNoPath, Normal), 5 seconds)

      assert(expectedResp.success == response.success)
      assert(expectedResp.message == response.message)
      assert(expectedResp.queryType == response.queryType)
      assert(expectedResp.anchors == response.anchors)
      assert(expectedResp.anchors == JNull)
      assert(expectedResp.event == response.event)
      assert(expectedResp.event == JNull)

    }

    "process successfully when Found with Type Signature" in {

      cassandra.executeScripts(
        CqlScript.statements(
          insertEventSql,
          insertLookupSql
        )
      )

      val modules: List[Module] = List {
        new LookupServiceBinder {

          override def gremlin: ScopedBindingBuilder = bind(classOf[Gremlin]).to(classOf[DefaultTestingGremlinConnector])

          override def finder: ScopedBindingBuilder = bind(classOf[Finder]).to(classOf[FakeFoundFinder])

        }

      }

      val value = "5aTelLQBerVT/vJiL2qjZCxWxqlfwT/BaID0zUVy7LyUC9nUdb02//aCiZ7xH1HglDqZ0Qqb7GyzF4jtBxfSBg=="
      val queryType = Signature
      val queryDepth = ShortestPath
      val responseForm = AnchorsWithPath
      val blockchainInfo = Normal

      val injector = new InjectorHelper(modules) {}
      val eventLogClient = injector.get[EventLogClient]

      val response = await(eventLogClient.getEventBySignature(value.getBytes, queryDepth, responseForm, blockchainInfo), 5 seconds)

      val expectedResp = LookupResult.Found(
        value = value,
        queryType = queryType,
        event = LookupJsonSupport.getJValue(data),
        anchors = EventLogClient.shortestPathAsJValue(FakeFoundFinder.simplePath, FakeFoundFinder.blockchains)
      )

      assert(expectedResp.success == response.success)
      assert(expectedResp.message == response.message)
      assert(expectedResp.queryType == response.queryType)
      assert(expectedResp.anchors == response.anchors)
      assert(expectedResp.event == response.event)

    }

    "handle json OK" in {

      val data = LookupJsonSupport.getJValue(
        """
          |{
          |   "hint":0,
          |   "payload":"c29tZSBieXRlcyEAAQIDnw==",
          |   "signature":"5aTelLQBerVT/vJiL2qjZCxWxqlfwT/BaID0zUVy7LyUC9nUdb02//aCiZ7xH1HglDqZ0Qqb7GyzF4jtBxfSBg==",
          |   "signed":"lRKwjni1ymWXEeiBhcg+pwAOTQCwc29tZSBieXRlcyEAAQIDnw==",
          |   "uuid":"8e78b5ca-6597-11e8-8185-c83ea7000e4d",
          |   "version":34
          |}
        """.stripMargin
      )

      val anchors = EventLogClient.shortestPathAsJValue(FakeFoundFinder.simplePath, FakeFoundFinder.blockchains)

      val value = LookupJsonSupport.ToJson(LookupResult(success = true, "value", Payload, "", data, anchors)).toString

      val expected = """{"success":true,"value":"value","query_type":"payload","message":"","event":{"hint":0,"payload":"c29tZSBieXRlcyEAAQIDnw==","signature":"5aTelLQBerVT/vJiL2qjZCxWxqlfwT/BaID0zUVy7LyUC9nUdb02//aCiZ7xH1HglDqZ0Qqb7GyzF4jtBxfSBg==","signed":"lRKwjni1ymWXEeiBhcg+pwAOTQCwc29tZSBieXRlcyEAAQIDnw==","uuid":"8e78b5ca-6597-11e8-8185-c83ea7000e4d","version":34},"anchors":{"shortest_path":[{"label":"UPP","properties":{"next_hash":"eUhr08+42ZL5KTwV6+QrbZ+HLGKgxGLHONd9WuZ6bt/wf/hBdMxHjPa+3Kb8aUC9yvhGHotGXZrvQZ2wpSe2HQ==","signature":"bCWe6UOwYCJlZ5nEQmiQrqzW7PwMl2DSi1loPNwMmukD9lnTm7xACePNP4BzzWt3NSvqTqC/Nqka/GBDVXDZAg==","hash":"/gQVsIcokNP8DF9J8dAz7u7QxMzCODjmZLWIyCI93Zw8j6WQsy9QTX2HgpRL5S3nuO40vldfvWERLiE3axJiXQ==","prev_hash":"/gQVsIcokNP8DF9J8dAz7u7QxMzCODjmZLWIyCI93Zw8j6WQsy9QTX2HgpRL5S3nuO40vldfvWERLiE3axJiXQ==","type":"UPP"}},{"label":"UPP","properties":{"next_hash":"18f8491f333b8ecf1bdb34db094284cb9416acc1ad04654617a05cdbb7686862f52f9963a681d11bb76e5bce680483f15f6916736855827bcf5fb11424d91fc6","signature":"YTKC9pYsKHaaxoz4g6r6MXgHq96eodAZWG5HaYHkPDX4hubgVtry36pypJORTGsYGujAfgtkhFyP1yYjdZZgDg==","hash":"eUhr08+42ZL5KTwV6+QrbZ+HLGKgxGLHONd9WuZ6bt/wf/hBdMxHjPa+3Kb8aUC9yvhGHotGXZrvQZ2wpSe2HQ==","prev_hash":"","type":"UPP"}},{"label":"SLAVE_TREE","properties":{"next_hash":"ec0a1fbd0b5be6dd8ef1095293053e4d62b7348f37ed29beede57066900de3e38d1d3de8769bc0d1b29f8f4593dcdb97e1bcf7b8b3b8227542b826fe993634a6","hash":"18f8491f333b8ecf1bdb34db094284cb9416acc1ad04654617a05cdbb7686862f52f9963a681d11bb76e5bce680483f15f6916736855827bcf5fb11424d91fc6","prev_hash":"eUhr08+42ZL5KTwV6+QrbZ+HLGKgxGLHONd9WuZ6bt/wf/hBdMxHjPa+3Kb8aUC9yvhGHotGXZrvQZ2wpSe2HQ==","type":"SLAVE_TREE"}},{"label":"MASTER_TREE","properties":{"next_hash":"Y9JAJGOZGLUQQFJOLEFVFUMTQILTZ9IKRPCFNEAGQEPRZPOWERJAQUQDCXHEUOCICGCSYCUBWDKBZ9999","hash":"ec0a1fbd0b5be6dd8ef1095293053e4d62b7348f37ed29beede57066900de3e38d1d3de8769bc0d1b29f8f4593dcdb97e1bcf7b8b3b8227542b826fe993634a6","prev_hash":"18f8491f333b8ecf1bdb34db094284cb9416acc1ad04654617a05cdbb7686862f52f9963a681d11bb76e5bce680483f15f6916736855827bcf5fb11424d91fc6","type":"MASTER_TREE"}}],"blockchains":[{"label":"PUBLIC_CHAIN","properties":{"public_chain":"IOTA_TESTNET_IOTA_TESTNET_NETWORK","hash":"Y9JAJGOZGLUQQFJOLEFVFUMTQILTZ9IKRPCFNEAGQEPRZPOWERJAQUQDCXHEUOCICGCSYCUBWDKBZ9999","prev_hash":"ec0a1fbd0b5be6dd8ef1095293053e4d62b7348f37ed29beede57066900de3e38d1d3de8769bc0d1b29f8f4593dcdb97e1bcf7b8b3b8227542b826fe993634a6","type":"PUBLIC_CHAIN"}}]}}""".stripMargin

      assert(value == expected)

    }

    "process successfully when Found with Anchors" in {

      import LookupKey._

      val modules: List[Module] = List {
        new LookupServiceBinder {

          override def gremlin: ScopedBindingBuilder = bind(classOf[Gremlin]).to(classOf[DefaultTestingGremlinConnector])

          override def finder: ScopedBindingBuilder = bind(classOf[Finder]).to(classOf[FakeFoundFinder])

        }
      }

      val injector = new InjectorHelper(modules) {}
      val eventsDAO = injector.get[EventsDAO]

      //We insert the PM Event
      val pmId = UUIDHelper.randomUUID.toString
      val pm = new ProtocolMessage(1, UUID.randomUUID(), 0, pmId)
      pm.setSignature(org.bouncycastle.util.Strings.toByteArray("1111"))
      pm.setChain(org.bouncycastle.util.Strings.toByteArray("this is my chain"))

      val maybeSignature = Option(pm)
        .flatMap(x => Option(x.getSignature))
        .map(x => org.bouncycastle.util.encoders.Base64.toBase64String(x))

      val signatureLookupKey = maybeSignature.map { x =>
        LookupKey(
          Values.SIGNATURE,
          Values.UPP_CATEGORY,
          pmId.asKey,
          Seq(x.asValue)
        )
      }.toSeq

      val pmAsJson = LookupJsonSupport.ToJson[ProtocolMessage](pm).get

      val el = EventLog("EventLogFromLookup", Values.UPP_CATEGORY, pmAsJson)
        .withLookupKeys(signatureLookupKey)
        .withCustomerId("1234")
        .withRandomNonce
        .withNewId(pmId)

      //End PM Insert

      val data: JValue = parse(""" { "id" : [1, 2, 3, 4] } """)

      //Slave Tree

      val slaveRootId = UUIDHelper.randomUUID.toString
      val slaveCategory = Values.SLAVE_TREE_CATEGORY
      val slaveTree = EventLog(data) //Lets say this is a tree.
        .withCategory(slaveCategory)
        .withCustomerId(Values.UBIRCH)
        .withServiceClass("ubirchChainerSlave")
        .withNewId(slaveRootId)
        .withRandomNonce
        .addLookupKeys(LookupKey(Values.SLAVE_TREE_ID, slaveCategory, slaveRootId.asKey, Seq(el.id.asValue)))

      //Master Tree
      val masterRootId = UUIDHelper.randomUUID.toString
      val masterCategory = Values.MASTER_TREE_CATEGORY
      val masterTree = EventLog(data) //Lets say this is a tree.
        .withCategory(masterCategory)
        .withCustomerId(Values.UBIRCH)
        .withServiceClass("ubirchChainerMaster")
        .withNewId(masterRootId)
        .withRandomNonce
        .addLookupKeys(LookupKey(Values.MASTER_TREE_ID, masterCategory, masterRootId.asKey, Seq(slaveTree.id.asValue)))
      //

      //Blockchain TX

      val tx = parse {
        """
          |{
          | "status": "added",
          | "txid": "51f6cfe400bd1062f8fcde5dc5c23aaac111e8124886ecf1f60c33015a35ccb0",
          | "message": "e392457bdd63db37d00435bfdc0a0a7f4a85f3664b9439956a4f4f2310fd934df85ea4a02823d4674c891f224bcab8c8f2c117fdc8710ce78c928fc9de8d9e19",
          | "blockchain": "ethereum",
          | "network_info": "Rinkeby Testnet Network",
          | "network_type": "testnet",
          | "created": "2019-05-07T21:30:14.421095"
          |}""".stripMargin
      }
      val txid = UUIDHelper.timeBasedUUID.toString

      val blockTx = EventLog("EventLogFromLookup", "blockchain_tx_id", tx)
        .withNewId(txid)
        .withLookupKeys(Seq(
          LookupKey(
            "blockchain_tx_id",
            Values.PUBLIC_CHAIN_CATEGORY,
            txid.asKey,
            Seq(masterTree.id.asValue)
          )
        ))

      await(eventsDAO.insertFromEventLog(el), 2 seconds)
      await(eventsDAO.insertFromEventLog(slaveTree), 2 seconds)
      await(eventsDAO.insertFromEventLog(masterTree), 2 seconds)
      await(eventsDAO.insertFromEventLog(blockTx), 2 seconds)

      val allEventLogs = Seq(el, slaveTree, masterTree, blockTx)

      val lookupKeys = allEventLogs.flatMap(_.lookupKeys)

      val all = await(eventsDAO.events.selectAll, 2 seconds)

      val allLookups = await(eventsDAO.lookups.selectAll, 2 seconds)

      assert(all.size == allEventLogs.size)
      assert(all.sortBy(_.id) == allEventLogs.map(EventLogRow.fromEventLog).sortBy(_.id))
      assert(allLookups.size == lookupKeys.flatMap(_.value).size)
      assert(allLookups.sortBy(_.name) == lookupKeys.flatMap(LookupKeyRow.fromLookUpKey).sortBy(_.name))
      assert(allLookups.size == 4)
    }

    "process successfully when Found with UpperLower" in {

      cassandra.executeScripts(
        CqlScript.statements(
          insertEventSql
        )
      )

      val modules: List[Module] = List {
        new LookupServiceBinder {

          override def gremlin: ScopedBindingBuilder = bind(classOf[Gremlin]).to(classOf[DefaultTestingGremlinConnector])

          override def finder: ScopedBindingBuilder = bind(classOf[Finder]).to(classOf[FakeFoundFinder])

        }
      }

      val injector = new InjectorHelper(modules) {}

      val eventLogClient = injector.get[EventLogClient]

      val value = "c29tZSBieXRlcyEAAQIDnw=="
      val queryType = Payload
      val queryDepth = UpperLower
      val responseForm = AnchorsWithPath
      val blockchainInfo = Normal

      val response = await(eventLogClient.getEventByHash(
        value.getBytes(),
        queryDepth,
        responseForm,
        blockchainInfo
      ), 5 seconds)

      val data =
        """
          |{
          |   "hint":0,
          |   "payload":"c29tZSBieXRlcyEAAQIDnw==",
          |   "signature":"5aTelLQBerVT/vJiL2qjZCxWxqlfwT/BaID0zUVy7LyUC9nUdb02//aCiZ7xH1HglDqZ0Qqb7GyzF4jtBxfSBg==",
          |   "signed":"lRKwjni1ymWXEeiBhcg+pwAOTQCwc29tZSBieXRlcyEAAQIDnw==",
          |   "uuid":"8e78b5ca-6597-11e8-8185-c83ea7000e4d",
          |   "version":34
          |}""".stripMargin

      val expectedLookup = LookupResult.Found(
        value = value,
        queryType = queryType,
        event = LookupJsonSupport.getJValue(data),
        anchors = EventLogClient.upperAndLowerAsJValue(FakeFoundFinder.upperPath, FakeFoundFinder.upperBlockchains, FakeFoundFinder.lowerPath, FakeFoundFinder.lowerBlockchains)
      )

      assert(response == expectedLookup)

    }

  }

  override protected def beforeEach(): Unit = {
    CollectorRegistry.defaultRegistry.clear()
    cassandra.executeScripts(CqlScript.statements("TRUNCATE events;", "TRUNCATE lookups;"))
    Thread.sleep(5000)
  }

  override protected def beforeAll(): Unit = {
    cassandra.start()
    cassandra.executeScripts(
      CqlScript.statements(
        "CREATE KEYSPACE IF NOT EXISTS event_log WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };",
        "USE event_log;",
        "drop table if exists events;",
        """
          |create table if not exists events (
          |    id text,
          |    customer_id text,
          |    service_class text,
          |    category text,
          |    signature text,
          |    event text,
          |    year int ,
          |    month int,
          |    day int,
          |    hour int,
          |    minute int,
          |    second int,
          |    milli int,
          |    event_time timestamp,
          |    nonce text,
          |    PRIMARY KEY ((id, category), year, month, day, hour)
          |) WITH CLUSTERING ORDER BY (year desc, month DESC, day DESC);
        """.stripMargin,
        "drop table if exists lookups;",
        """
          |create table if not exists lookups (
          |    key text,
          |    value text,
          |    name text,
          |    category text,
          |    PRIMARY KEY ((value, category), name)
          |);
        """.stripMargin
      )
    )
  }

  override protected def afterAll(): Unit = {
    cassandra.stop()
  }

}

