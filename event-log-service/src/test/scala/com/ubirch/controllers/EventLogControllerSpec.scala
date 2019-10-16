package com.ubirch.controllers

import com.github.nosan.embedded.cassandra.cql.CqlScript
import com.ubirch.TestBase
import com.ubirch.models.{ EventLogGenericResponse, Values }
import com.ubirch.service.ExtServiceBinder
import com.ubirch.services.ServiceBinder
import com.ubirch.util.{ EventLogJsonSupport, InjectorHelper }
import io.prometheus.client.CollectorRegistry

class EventLogControllerSpec extends TestBase {

  cassandra.start()

  object Inject extends InjectorHelper(ServiceBinder.modules ++ ExtServiceBinder.modules)

  addServlet(Inject.get[EventLogController], "/*")

  "GET / ON EventLogController " must {

    "return 200 and the API info" in {
      get("/") {
        val expectedBody = """{"success":true,"message":"Successfully Processed","data":{"name":"Event Log Service","description":"These are the available endpoints for querying the Event Log Service","version":"1.3.0"}}""".stripMargin
        assert(200 == status)
        assert(body == expectedBody)
      }
    }

  }

  "GET /events ON EventLogController" must {

    def params(category: String, year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, milli: Int) =
      s"?category=${category}&year=${year}&month=${month}&day=${day}&hour=${hour}&minute=${minute}&second=${second}&milli=${milli}"

    "return 200 and a valid body data" in {

      val insertEventSql: String =
        s"""
           |INSERT INTO events (id, customer_id, service_class, category, event, event_time, year, month, day, hour, minute, second, milli, signature, nonce)
           | VALUES ('c29tZSBieXRlcyEAAQIDnw==', 'customer_id', 'service_class', '${Values.MASTER_TREE_CATEGORY}', '{
           |   "hint":0,
           |   "payload":"c29tZSBieXRlcyEAAQIDnw==",
           |   "signature":"5aTelLQBerVT/vJiL2qjZCxWxqlfwT/BaID0zUVy7LyUC9nUdb02//aCiZ7xH1HglDqZ0Qqb7GyzF4jtBxfSBg==",
           |   "signed":"lRKwjni1ymWXEeiBhcg+pwAOTQCwc29tZSBieXRlcyEAAQIDnw==",
           |   "uuid":"8e78b5ca-6597-11e8-8185-c83ea7000e4d",
           |   "version":34
           |}', '1986-03-02T17:00:28.333Z', 1986, 3, 2, 17, 439, 16, 0, '0681D35827B17104A2AACCE5A08C4CD1BC8A5EF5EFF4A471D15976693CC0D6D67392F1CACAE63565D6E521D2325A998CDE00A2FEF5B65D0707F4158000EB6D05',
           |'34376336396166392D336533382D343665652D393063332D616265313364383335353266');
    """.stripMargin

      cassandra.executeScripts(
        CqlScript.statements(
          insertEventSql
        )
      )

      val expectedBody = """{"success":true,"message":"Request successfully processed","data":[{"headers":{},"id":"c29tZSBieXRlcyEAAQIDnw==","customer_id":"customer_id","service_class":"service_class","category":"MASTER_TREE","event":{"hint":0,"payload":"c29tZSBieXRlcyEAAQIDnw==","signature":"5aTelLQBerVT/vJiL2qjZCxWxqlfwT/BaID0zUVy7LyUC9nUdb02//aCiZ7xH1HglDqZ0Qqb7GyzF4jtBxfSBg==","signed":"lRKwjni1ymWXEeiBhcg+pwAOTQCwc29tZSBieXRlcyEAAQIDnw==","uuid":"8e78b5ca-6597-11e8-8185-c83ea7000e4d","version":34},"event_time":"1986-03-02T17:00:28.333Z","signature":"0681D35827B17104A2AACCE5A08C4CD1BC8A5EF5EFF4A471D15976693CC0D6D67392F1CACAE63565D6E521D2325A998CDE00A2FEF5B65D0707F4158000EB6D05","nonce":"34376336396166392D336533382D343665652D393063332D616265313364383335353266","lookup_keys":[]}]}""".stripMargin

      get("/events" + params("MASTER_TREE", 1986, 3, 2, -1, -1, -1, -1)) {
        val resp = EventLogJsonSupport.FromString[EventLogGenericResponse](body).get
        assert(200 == status)
        assert(resp.success)
        assert(resp.message == "Request successfully processed")
        assert(resp.data.nonEmpty)
        assert(expectedBody == body)
      }
    }

    "return 200 and a valid body data when there are multiple records" in {

      val truncate = "truncate events;"

      val insertEventSql1: String =
        s"""
           |INSERT INTO events (id, customer_id, service_class, category, event, event_time, year, month, day, hour, minute, second, milli, signature, nonce)
           | VALUES ('c29tZSBieXRlcyEAAQIDnw==', 'customer_id', 'service_class', '${Values.MASTER_TREE_CATEGORY}', '{
           |   "hint":0,
           |   "payload":"c29tZSBieXRlcyEAAQIDnw==",
           |   "signature":"5aTelLQBerVT/vJiL2qjZCxWxqlfwT/BaID0zUVy7LyUC9nUdb02//aCiZ7xH1HglDqZ0Qqb7GyzF4jtBxfSBg==",
           |   "signed":"lRKwjni1ymWXEeiBhcg+pwAOTQCwc29tZSBieXRlcyEAAQIDnw==",
           |   "uuid":"8e78b5ca-6597-11e8-8185-c83ea7000e4d",
           |   "version":34
           |}', '1986-03-02T17:00:28.333Z', 1986, 3, 2, 17, 439, 16, 0, '0681D35827B17104A2AACCE5A08C4CD1BC8A5EF5EFF4A471D15976693CC0D6D67392F1CACAE63565D6E521D2325A998CDE00A2FEF5B65D0707F4158000EB6D05',
           |'34376336396166392D336533382D343665652D393063332D616265313364383335353266');
    """.stripMargin

      val insertEventSql2: String =
        s"""
           |INSERT INTO events (id, customer_id, service_class, category, event, event_time, year, month, day, hour, minute, second, milli, signature, nonce)
           | VALUES ('E29tZSBieXRlcyEAAQIDnw==', 'customer_id', 'service_class', '${Values.MASTER_TREE_CATEGORY}', '{
           |   "hint":0,
           |   "payload":"E29tZSBieXRlcyEAAQIDnw==",
           |   "signature":"5aTelLQBerVT/vJiL2qjZCxWxqlfwT/BaID0zUVy7LyUC9nUdb02//aCiZ7xH1HglDqZ0Qqb7GyzF4jtBxfSBg==",
           |   "signed":"lRKwjni1ymWXEeiBhcg+pwAOTQCwc29tZSBieXRlcyEAAQIDnw==",
           |   "uuid":"8e78b5ca-6597-11e8-8185-c83ea7000e4d",
           |   "version":34
           |}', '1986-03-02T18:00:28.333Z', 1986, 3, 2, 18, 439, 16, 0, 'E681D35827B17104A2AACCE5A08C4CD1BC8A5EF5EFF4A471D15976693CC0D6D67392F1CACAE63565D6E521D2325A998CDE00A2FEF5B65D0707F4158000EB6D05',
           |'44376336396166392D336533382D343665652D393063332D616265313364383335353266');
    """.stripMargin

      cassandra.executeScripts(
        CqlScript.statements(
          truncate,
          insertEventSql1,
          insertEventSql2
        )
      )

      val expectedBody = """{"success":true,"message":"Request successfully processed","data":[{"headers":{},"id":"E29tZSBieXRlcyEAAQIDnw==","customer_id":"customer_id","service_class":"service_class","category":"MASTER_TREE","event":{"hint":0,"payload":"E29tZSBieXRlcyEAAQIDnw==","signature":"5aTelLQBerVT/vJiL2qjZCxWxqlfwT/BaID0zUVy7LyUC9nUdb02//aCiZ7xH1HglDqZ0Qqb7GyzF4jtBxfSBg==","signed":"lRKwjni1ymWXEeiBhcg+pwAOTQCwc29tZSBieXRlcyEAAQIDnw==","uuid":"8e78b5ca-6597-11e8-8185-c83ea7000e4d","version":34},"event_time":"1986-03-02T18:00:28.333Z","signature":"E681D35827B17104A2AACCE5A08C4CD1BC8A5EF5EFF4A471D15976693CC0D6D67392F1CACAE63565D6E521D2325A998CDE00A2FEF5B65D0707F4158000EB6D05","nonce":"44376336396166392D336533382D343665652D393063332D616265313364383335353266","lookup_keys":[]},{"headers":{},"id":"c29tZSBieXRlcyEAAQIDnw==","customer_id":"customer_id","service_class":"service_class","category":"MASTER_TREE","event":{"hint":0,"payload":"c29tZSBieXRlcyEAAQIDnw==","signature":"5aTelLQBerVT/vJiL2qjZCxWxqlfwT/BaID0zUVy7LyUC9nUdb02//aCiZ7xH1HglDqZ0Qqb7GyzF4jtBxfSBg==","signed":"lRKwjni1ymWXEeiBhcg+pwAOTQCwc29tZSBieXRlcyEAAQIDnw==","uuid":"8e78b5ca-6597-11e8-8185-c83ea7000e4d","version":34},"event_time":"1986-03-02T17:00:28.333Z","signature":"0681D35827B17104A2AACCE5A08C4CD1BC8A5EF5EFF4A471D15976693CC0D6D67392F1CACAE63565D6E521D2325A998CDE00A2FEF5B65D0707F4158000EB6D05","nonce":"34376336396166392D336533382D343665652D393063332D616265313364383335353266","lookup_keys":[]}]}""".stripMargin

      get("/events" + params("MASTER_TREE", 1986, 3, 2, -1, -1, -1, -1)) {
        val resp = EventLogJsonSupport.FromString[EventLogGenericResponse](body).get
        assert(200 == status)
        assert(resp.success)
        assert(resp.message == "Request successfully processed")
        assert(resp.data.size == 2)
        assert(expectedBody == body)
        val topDate = resp.data.headOption.map(_.eventTime).get
        val tailDate = resp.data.tail.headOption.map(_.eventTime).get
        assert(topDate after tailDate)
      }
    }

    "return 404 and a valid body when nothing found" in {
      get("/events" + params("MASTER_TREE", 1986, 3, 2, -1, -1, -1, -1)) {
        val resp = EventLogJsonSupport.FromString[EventLogGenericResponse](body).get
        assert(404 == status)
        assert(resp.success)
        assert(resp.message == "Request successfully processed")
        assert(resp.data.isEmpty)
        assert("""{"success":true,"message":"Request successfully processed","data":[]}""" == body)
      }
    }

    "return 404 when invalid params are provided " in {
      get("/events") {
        val resp = EventLogJsonSupport.FromString[EventLogGenericResponse](body).get
        assert(400 == status)
        assert(!resp.success)
        assert(resp.message == "Error reading query params")
        assert(resp.data.isEmpty)
        assert("""{"success":false,"message":"Error reading query params","data":[]}""" == body)
      }
    }

    "return 404 and correct body when no route exists " in {
      get("/routedoesntexist") {
        val resp = EventLogJsonSupport.FromString[EventLogGenericResponse](body).get
        assert(404 == status)
        assert(!resp.success)
        assert(resp.message == "Route not found")
        assert(resp.data.isEmpty)
        assert("""{"success":false,"message":"Route not found","data":[]}""" == body)
      }
    }

  }

  override protected def beforeEach(): Unit = {
    CollectorRegistry.defaultRegistry.clear()
    cassandra.executeScripts(CqlScript.statements("TRUNCATE events;", "TRUNCATE lookups;"))
    Thread.sleep(5000)
  }

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    cassandra.executeScripts(
      CqlScript.statements(
        "CREATE KEYSPACE IF NOT EXISTS event_log WITH REPLICATION = { 'class' : 'SimpleStrategy', 'replication_factor' : 1 };",
        "USE event_log;",
        "drop table if exists events;",
        """
          |create table if not exists events
          |(
          |    id            text,
          |    customer_id   text,
          |    service_class text,
          |    category      text,
          |    signature     text,
          |    event         text,
          |    year          int,
          |    month         int,
          |    day           int,
          |    hour          int,
          |    minute        int,
          |    second        int,
          |    milli         int,
          |    event_time    timestamp,
          |    nonce         text,
          |    PRIMARY KEY ((id, category), year, month, day, hour, minute, second, milli)
          |) WITH CLUSTERING ORDER BY (year DESC, month DESC, day DESC, hour DESC, minute DESC, second DESC, milli DESC);
          |""".stripMargin,
        "drop table if exists lookups;",
        """
          |create table if not exists lookups (
          |    key text,
          |    value text,
          |    name text,
          |    category text,
          |    PRIMARY KEY ((value, category), name)
          |);
        """.stripMargin,
        "drop MATERIALIZED VIEW IF exists events_by_cat;",
        """
          |CREATE MATERIALIZED VIEW events_by_cat AS
          |SELECT *
          |FROM events
          |WHERE category IS NOT null
          |  and year is not null
          |  and month is not null
          |  and day is not null
          |  and hour is not null
          |  and minute is not null
          |  and second is not null
          |  and milli is not null
          |  and id is not null
          |    PRIMARY KEY ((category, year, month), day, hour, minute, second, milli, id)
          |    WITH CLUSTERING ORDER BY (day DESC, hour DESC, minute DESC, second DESC, milli DESC, id ASC);
          |""".stripMargin
      )
    )
  }

  override protected def afterAll(): Unit = {
    cassandra.stop()
  }

}
