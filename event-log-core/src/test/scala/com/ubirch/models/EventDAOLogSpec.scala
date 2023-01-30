package com.ubirch.models

import java.util.Date
import com.github.nosan.embedded.cassandra.cql.{ CqlScript, StringCqlScript }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.util.{ InjectorHelper, UUIDHelper }
import com.ubirch.TestBase
import com.ubirch.util.cassandra.test.EmbeddedCassandraBase
import io.prometheus.client.CollectorRegistry
import org.json4s.JValue
import org.json4s.jackson.JsonMethods.parse

import scala.concurrent.duration._
import scala.language.postfixOps

class EventDAOLogSpec extends TestBase with EmbeddedCassandraBase with LazyLogging {

  val cassandra = new CassandraTest
  import LookupKey._

  "Event Log Model" must {

    "insert and get all and find one and check lookup keys" in {
      val data: JValue = parse(""" { "id" : [1, 2, 3, 4] } """)

      def date = new Date()

      val key = UUIDHelper.timeBasedUUID.toString

      def id = UUIDHelper.timeBasedUUID.toString

      val category = "my category id"

      val events = (0 to 5).map { _ =>
        val _id = id
        EventLog(data)
          .withNewId(_id)
          .withCustomerId("my customer id")
          .withCategory(category)
          .withServiceClass("my service class")
          .withEventTime(date)
          .withSignature("my signature")
          .withNonce("my nonce")
          .withHeaders("hola" -> "Hello", "hey" -> "como estas")
          .addLookupKeys(LookupKey("lookupname", category, key.asKey, Seq(_id.asValue)))
      }

      val eventsDAO = InjectorHelper.get[EventsDAO]

      events.map(el => await(eventsDAO.insertFromEventLog(el), 2 seconds))
      val all = await(eventsDAO.events.selectAll, 2 seconds)

      assert(all.size == events.size)
      assert(events.map(x => EventLogRow.fromEventLog(x)).sortBy(_.id).toList == all.sortBy(_.id))

      val eventToFind = events.headOption
      val idToFind = eventToFind.map(_.id).getOrElse("")
      val one = await(eventsDAO.events.byIdAndCat(idToFind, category), 2 seconds)

      assert(one.size == 1)
      assert(eventToFind.map(x => EventLogRow.fromEventLog(x)).toList == one)

      val lookupKeys = await(eventsDAO.lookups.selectAll, 2 seconds)

      assert(lookupKeys.nonEmpty)
      assert(lookupKeys.size == events.flatMap(_.lookupKeys).size)
      assert(lookupKeys.sortBy(_.key).sortBy(_.value) == events.flatMap(_.lookupKeys).flatMap(x => LookupKeyRow.fromLookUpKey(x)).toList)
      assert(lookupKeys.size == 6)

    }

    "eventLogRowByLookupRowInfo" in {
      val data: JValue = parse(""" { "id" : [1, 2, 3, 4] } """)

      def date = new Date()

      val key = UUIDHelper.timeBasedUUID.toString
      val value = UUIDHelper.timeBasedUUID.toString
      val category = "my category id"
      val lookupName = "lookupname"

      val event = {
        EventLog(data)
          .withNewId(key)
          .withCustomerId("my customer id")
          .withCategory(category)
          .withServiceClass("my service class")
          .withEventTime(date)
          .withSignature("my signature")
          .withNonce("my nonce")
          .withHeaders("hola" -> "Hello", "hey" -> "como estas")
          .addLookupKeys(LookupKey(lookupName, category, key.asKey, Seq(value.asValue)))
      }

      val eventsDAO = InjectorHelper.get[EventsDAO]

      await(eventsDAO.insertFromEventLog(event), 2 seconds)

      val foundEvent = await(eventsDAO.eventLogRowByLookupRowInfo(value, lookupName, category), 2 seconds)

      assert(foundEvent.isDefined)
      assert(EventLogRow.fromEventLog(event) == foundEvent.get)

    }

    "eventLogRowByLookupValueAndCategory" in {
      val data: JValue = parse(""" { "id" : [1, 2, 3, 4] } """)

      def date = new Date()

      val category = "my category id"
      val lookupName = "lookupname"

      val event1 = {
        EventLog(data)
          .withNewId
          .withCustomerId("my customer id")
          .withCategory(category)
          .withServiceClass("my service class")
          .withEventTime(date)
          .withSignature("my signature")
          .withNonce("my nonce")
          .withHeaders("hola" -> "Hello", "hey" -> "como estas")
      }

      val event2 = {
        val event2Id = UUIDHelper.timeBasedUUID.toString
        EventLog(data)
          .withNewId
          .withCustomerId("my customer id")
          .withCategory(category)
          .withServiceClass("my service class")
          .withEventTime(date)
          .withSignature("my signature")
          .withNonce("my nonce")
          .withHeaders("hola" -> "Hello", "hey" -> "como estas")
          .addLookupKeys(LookupKey(lookupName, category, event2Id.asKey, Seq(event1.id.asValue)))

      }

      val event3 = {
        val event3Id = UUIDHelper.timeBasedUUID.toString

        EventLog(data)
          .withNewId(event3Id)
          .withCustomerId("my customer id")
          .withCategory(lookupName)
          .withServiceClass("my service class")
          .withEventTime(date)
          .withSignature("my signature")
          .withNonce("my nonce")
          .withHeaders("hola" -> "Hello", "hey" -> "como estas")
          .addLookupKeys(LookupKey(lookupName, category, event3Id.asKey, Seq(event1.id.asValue)))

      }

      val eventsDAO = InjectorHelper.get[EventsDAO]

      await(eventsDAO.insertFromEventLog(event1), 2 seconds)
      await(eventsDAO.insertFromEventLog(event2), 2 seconds)
      await(eventsDAO.insertFromEventLog(event3), 2 seconds)

      val foundEvent = await(eventsDAO.eventLogRowByLookupValueAndCategory(event1.id, category), 2 seconds)
      assert(foundEvent.nonEmpty)
      assert(Seq(EventLogRow.fromEventLog(event3)) == foundEvent)
    }

    "eventLogRowByLookupValueAndCategory 2" in {
      val data: JValue = parse(""" { "id" : [1, 2, 3, 4] } """)

      def date = new Date()

      val genericCategory = Values.PUBLIC_CHAIN_CATEGORY

      val event1 = {
        EventLog(data)
          .withNewId
          .withCustomerId("my customer id")
          .withCategory(genericCategory)
          .withServiceClass("my service class")
          .withEventTime(date)
          .withSignature("my signature")
          .withNonce("my nonce")
          .withHeaders("hola" -> "Hello", "hey" -> "como estas")
      }

      val event2 = {
        EventLog(data)
          .withNewId
          .withCustomerId("my customer id")
          .withCategory(genericCategory)
          .withServiceClass("my service class")
          .withEventTime(date)
          .withSignature("my signature")
          .withNonce("my nonce")
          .withHeaders("hola" -> "Hello", "hey" -> "como estas")

      }

      val uniqueCategory = "Ethereum"
      val event3 = {
        val event3Id = UUIDHelper.timeBasedUUID.toString

        EventLog(data)
          .withNewId(event3Id)
          .withCustomerId("my customer id")
          .withCategory(uniqueCategory)
          .withServiceClass("my service class")
          .withEventTime(date)
          .withSignature("my signature")
          .withNonce("my nonce")
          .withHeaders("hola" -> "Hello", "hey" -> "como estas")
          .addLookupKeys(LookupKey("Ethereum", genericCategory, event3Id.asKey, Seq(event1.id.asValue, event2.id.asValue)))

      }

      val eventsDAO = InjectorHelper.get[EventsDAO]

      await(eventsDAO.insertFromEventLog(event1), 2 seconds)
      await(eventsDAO.insertFromEventLog(event2), 2 seconds)
      await(eventsDAO.insertFromEventLog(event3), 2 seconds)

      val all = await(eventsDAO.events.selectAll, 2 seconds)
      val foundEvent = await(eventsDAO.eventLogRowByLookupValueAndCategory(event1.id, genericCategory), 2 seconds)
      val expected = Seq(EventLogRow.fromEventLog(event3))

      assert(all.size == 3)
      assert(foundEvent.nonEmpty)
      assert(foundEvent.size == 1)
      assert(expected.sortBy(_.id) == foundEvent.sortBy(_.id))
      assert(expected == foundEvent)
      assert(uniqueCategory == foundEvent.map(_.category).headOption.getOrElse(""))

    }

    "eventLogRowByLookupValueAndCategory 3" in {
      val data: JValue = parse(""" { "id" : [1, 2, 3, 4] } """)

      def date = new Date()

      val event = {
        EventLog(data)
          .withNewId
          .withCustomerId("my customer id")
          .withCategory("UPP")
          .withServiceClass("my service class")
          .withEventTime(date)
          .withSignature("my signature")
          .withNonce("my nonce")
          .withHeaders("hola" -> "Hello", "hey" -> "como estas")
      }

      val tree = {
        val treeId = UUIDHelper.randomUUID.toString
        EventLog(data)
          .withNewId(treeId)
          .withCustomerId("my customer id")
          .withCategory("TREE")
          .withServiceClass("my service class")
          .withEventTime(date)
          .withSignature("my signature")
          .withNonce("my nonce")
          .withHeaders("hola" -> "Hello", "hey" -> "como estas")
          .addLookupKeys(LookupKey("tree_id", "TREE", treeId.asKey, Seq(event.id.asValue)))
      }

      val tx1 = {
        val txId = UUIDHelper.randomUUID.toString
        EventLog(data)
          .withNewId(txId)
          .withCustomerId("my customer id")
          .withCategory("Ethereum")
          .withServiceClass("my service class")
          .withEventTime(date)
          .withSignature("my signature")
          .withNonce("my nonce")
          .withHeaders("hola" -> "Hello", "hey" -> "como estas")
          .addLookupKeys(LookupKey("Ethereum", "PUBLIC_CHAIN", txId.asKey, Seq(tree.id.asValue)))
      }

      val tx2 = {
        val txId = UUIDHelper.randomUUID.toString
        EventLog(data)
          .withNewId(txId)
          .withCustomerId("my customer id")
          .withCategory("EthereumClassic")
          .withServiceClass("my service class")
          .withEventTime(date)
          .withSignature("my signature")
          .withNonce("my nonce")
          .withHeaders("hola" -> "Hello", "hey" -> "como estas")
          .addLookupKeys(LookupKey("EthereumClassic", "PUBLIC_CHAIN", txId.asKey, Seq(tree.id.asValue)))
      }

      val eventsDAO = InjectorHelper.get[EventsDAO]

      await(eventsDAO.insertFromEventLog(event), 2 seconds)
      await(eventsDAO.insertFromEventLog(tree), 2 seconds)
      await(eventsDAO.insertFromEventLog(tx1), 2 seconds)
      await(eventsDAO.insertFromEventLog(tx2), 2 seconds)

      val all = await(eventsDAO.events.selectAll, 2 seconds)
      val treeFound = await(eventsDAO.eventLogRowByLookupRowInfo(event.id, "tree_id", "TREE"), 2 seconds)
      val txFound = await(eventsDAO.eventLogRowByLookupValueAndCategory(tree.id, "PUBLIC_CHAIN"), 2 seconds)
      val expected = Seq(EventLogRow.fromEventLog(tx1), EventLogRow.fromEventLog(tx2))

      assert(all.size == 4)
      assert(treeFound.nonEmpty)
      assert(treeFound.get == EventLogRow.fromEventLog(tree))
      assert(txFound.size == 2)
      assert(expected.sortBy(_.id) == txFound.sortBy(_.id))
      assert(expected == txFound)

    }

    "insert and delete all event logs" in {
      val data: JValue = parse(""" { "id" : [1, 2, 3, 4] } """)

      def date = new Date()

      val key = UUIDHelper.timeBasedUUID.toString

      def id = UUIDHelper.timeBasedUUID.toString

      val category = "my category id"

      val events = (0 to 5).map { _ =>
        val _id = id
        EventLog(data)
          .withNewId(_id)
          .withCustomerId("my customer id")
          .withCategory(category)
          .withServiceClass("my service class")
          .withEventTime(date)
          .withSignature("my signature")
          .withNonce("my nonce")
          .withHeaders("hola" -> "Hello", "hey" -> "como estas")
          .addLookupKeys(LookupKey("lookupname", category, key.asKey, Seq(_id.asValue)))
      }

      val eventsDAO = InjectorHelper.get[EventsDAO]

      // insert event logs
      events.map(el => await(eventsDAO.insertFromEventLog(el), 2 seconds))
      val all = await(eventsDAO.events.selectAll, 2 seconds)

      assert(all.size == events.size)
      assert(events.map(x => EventLogRow.fromEventLog(x)).sortBy(_.id).toList == all.sortBy(_.id))

      // delete event logs
      events.map(e => await(eventsDAO.deleteFromEventLog(e), 2 seconds))

      val allAfterDeleted = await(eventsDAO.events.selectAll, 2 seconds)
      assert(allAfterDeleted.size == 0)
    }

  }

  override protected def beforeEach(): Unit = {
    CollectorRegistry.defaultRegistry.clear()
    cassandra.executeScripts(List(new StringCqlScript("TRUNCATE events;"), new StringCqlScript("TRUNCATE lookups;")))
    Thread.sleep(5000)
  }

  override protected def beforeAll(): Unit = {
    cassandra.start()
    cassandra.executeScripts(
      List(
        new StringCqlScript("CREATE KEYSPACE IF NOT EXISTS event_log WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };"),
        new StringCqlScript("USE event_log;"),
        new StringCqlScript("drop table if exists events;"),
        new StringCqlScript("""
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
        """.stripMargin),
        new StringCqlScript("drop table if exists lookups;"),
        new StringCqlScript("""
          |create table if not exists lookups (
          |    key text,
          |    value text,
          |    name text,
          |    category text,
          |    PRIMARY KEY ((value, category), name)
          |);
        """.stripMargin)
      )
    )
  }

  override protected def afterAll(): Unit = {
    cassandra.stop()
  }

}
