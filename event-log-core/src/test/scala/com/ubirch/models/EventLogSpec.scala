package com.ubirch.models

import java.text.SimpleDateFormat
import java.util.Date

import com.ubirch.services.config.ConfigProvider
import com.ubirch.util.{ EventLogJsonSupport, JsonHelper, SigningHelper, UUIDHelper }
import com.ubirch.{ Entities, TestBase }
import org.json4s.jackson.JsonMethods.parse
import com.ubirch.models.EnrichedEventLog.enrichedEventLog
import org.json4s.{ JValue, MappingException }
import org.scalatest.mockito.MockitoSugar

class EventLogSpec extends TestBase with MockitoSugar {

  "Event Log Model" must {

    "Extract data from path" in {
      val data: JValue = parse(""" { "id" : [1, 2, 3, 4] } """)

      val date = new Date()

      val eventLog = EventLog(data)
        .withNewId
        .withCustomerId("my customer id")
        .withCategory("my customer id")
        .withServiceClass("my service class")
        .withCategory("my category")
        .withEventTime(date)
        .withSignature("my signature")
        .withNonce("my nonce")
        .withHeaders("hola" -> "Hello", "hey" -> "como estas")

      val eventLogJson = EventLogJsonSupport.ToJson[EventLog](eventLog).get \ "id"

      import EventLogJsonSupport._
      assert(eventLog.id == eventLogJson.extractOpt[String].getOrElse(""))

    }

    "generate with headers" in {

      val data: JValue = parse(""" { "numbers" : [1, 2, 3, 4] } """)

      val eventLog = EventLog(data)
        .withNewId("this is my unique id")
        .withHeaders("hola" -> "Hola", "adios" -> "Hello")

      assert(eventLog.id == "this is my unique id")
      assert(eventLog.headers == Headers.create("hola" -> "Hola", "adios" -> "Hello"))

    }

    "replace headers" in {

      val data: JValue = parse(""" { "numbers" : [1, 2, 3, 4] } """)

      val eventLog = EventLog(data)
        .withNewId("this is my unique id")
        .withHeaders("hola" -> "Hola", "adios" -> "Hello")
        .replaceHeaders("adios" -> "goodbye")

      assert(eventLog.id == "this is my unique id")
      assert(eventLog.headers == Headers.create("hola" -> "Hola", "adios" -> "goodbye"))

    }

    "remove headers" in {

      val data: JValue = parse(""" { "numbers" : [1, 2, 3, 4] } """)

      val eventLog = EventLog(data)
        .withNewId("this is my unique id")
        .withHeaders("hola" -> "Hola", "adios" -> "Hello")
        .removeHeaders

      assert(eventLog.id == "this is my unique id")
      assert(eventLog.headers == Headers.create())

    }

    "add header" in {

      val data: JValue = parse(""" { "numbers" : [1, 2, 3, 4] } """)

      val eventLog = EventLog(data)
        .withNewId("this is my unique id")
        .withHeaders("hola" -> "Hola", "adios" -> "goodbye")
        .addHeaders("what" -> "siiiii")

      assert(eventLog.id == "this is my unique id")
      assert(eventLog.headers == Headers.create("hola" -> "Hola", "adios" -> "goodbye", "what" -> "siiiii"))

    }

    "generate new id as string" in {

      val data: JValue = parse(""" { "numbers" : [1, 2, 3, 4] } """)

      val eventLog = EventLog(data).withNewId("this is my unique id")

      assert(eventLog.id == "this is my unique id")

    }

    "generate new uuid" in {

      val uuid = UUIDHelper.timeBasedUUID
      val eventLog = Entities.Events.eventExample()

      assert(uuid.toString == eventLog.withNewId(uuid).id)

      assert(eventLog.id != eventLog.withNewId.id)

    }

    "generate new random nonce" in {

      val nonce = UUIDHelper.randomUUID.toString
      val eventLog = Entities.Events.eventExample()

      assert(nonce == eventLog.withNonce(nonce).nonce)

      assert(eventLog.nonce != eventLog.withRandomNonce.nonce)

    }

    "generate category" in {

      val category = "NEW CAT"

      val eventLog = Entities.Events.eventExample().withCategory(category)

      assert(category == eventLog.category)
    }

    "generate service" in {

      val service = "NEW SERVICE CLASS"

      val eventLog = Entities.Events.eventExample().withServiceClass(service)

      assert(service == eventLog.serviceClass)
    }

    "generate event time" in {

      val eventLog = Entities.Events.eventExample()

      val sdf = new SimpleDateFormat("dd-M-yyyy hh:mm:ss")
      val newDate = sdf.parse("02-03-1986 16:00:00")
      val newEventLog = eventLog.withEventTime(newDate)

      assert(newDate == newEventLog.eventTime)

      Thread.sleep(1000)

      assert(eventLog.eventTime.getTime < newEventLog.withCurrentEventTime.eventTime.getTime)

    }

    "have toJson" in {
      val eventLog = Entities.Events.eventExample()
      assert(eventLog.toJson == EventLogJsonSupport.ToJson[EventLog](eventLog).toString)
    }

    "have the same object after de/serialization " in {

      val data: JValue = parse(""" { "numbers" : [1, 2, 3, 4] } """)

      val date = new Date()

      val eventLog = EventLog(data)
        .withNewId("my id")
        .withCustomerId("my customer id")
        .withCategory("my customer id")
        .withServiceClass("my service class")
        .withCategory("my category")
        .withEventTime(date)
        .withSignature("my signature")
        .withNonce("my nonce")
        .withHeaders("hola" -> "Hello", "hey" -> "como estas")

      val eventLogJson = EventLogJsonSupport.ToJson[EventLog](eventLog).get

      val eventLog2 = EventLogJsonSupport.FromJson[EventLog](eventLogJson).get

      assert(eventLog == eventLog2)
    }

    "fail convert string to type when date format is wrong" in {
      val event =
        """
          |{
          |"id":"243f7063-6126-470e-9947-be49a62351c0",
          |"service_class":"this is a service class",
          |"category":"this is a category",
          |"event":{"numbers":[1,2,3,4]},
          |"event_time":"Mon Jan 28 22:07:52 CET 2019",
          |"signature":"this is a signature",
          |"nonce":"THIS IS A NONCE"
          |}""".stripMargin

      val fromJson = EventLogJsonSupport.FromString[EventLog](event)

      assertThrows[MappingException](fromJson.get.toJson)
    }

    "fail convert when headers aren't in proper format" in {
      val event =
        """
          |{
          |"headers": {"hola": "Hello"},
          |"id":"243f7063-6126-470e-9947-be49a62351c0",
          |"service_class":"this is a service class",
          |"category":"this is a category",
          |"event":{"numbers":[1,2,3,4]},
          |"event_time":"2019-01-29T17:00:28.333Z",
          |"signature":"this is a signature",
          |"nonce":"THIS IS A NONCE"
          |}""".stripMargin

      val fromJson = EventLogJsonSupport.FromString[EventLog](event)

      assertThrows[MappingException](fromJson.get.toJson)
    }

    "get same fields with a uuid id" in {
      val event =
        """
          |{
          |"headers":{"hola":["Hola"],"adios":["goodbye"]},
          |"id":"61002dd0-23e7-11e9-8be0-61a26140e9b5",
          |"customer_id": "61002dd0-23e7-11e9-8be0-61a26140e9b5",
          |"service_class":"com.ubirch.sdk.EventLogging",
          |"category":"My Category",
          |"event":{"name":"Hola"},
          |"event_time":"2019-01-29T17:00:28.333Z",
          |"signature":"THIS IS A SIGNATURE",
          |"nonce":"THIS IS A NONCE"
          |}""".stripMargin

      val fromJson = EventLogJsonSupport.FromString[EventLog](event).get

      assert(fromJson.headers == Headers.create("hola" -> "Hola", "adios" -> "goodbye"))
      assert(fromJson.id == "61002dd0-23e7-11e9-8be0-61a26140e9b5")
      assert(fromJson.customerId == "61002dd0-23e7-11e9-8be0-61a26140e9b5")
      assert(fromJson.serviceClass == "com.ubirch.sdk.EventLogging")
      assert(fromJson.category == "My Category")
      assert(fromJson.event == EventLogJsonSupport.getJValue("""{"name":"Hola"}"""))
      assert(fromJson.eventTime == JsonHelper.formats.dateFormat.parse("2019-01-29T17:00:28.333Z").getOrElse(new Date))
      assert(fromJson.signature == "THIS IS A SIGNATURE")
      assert(fromJson.nonce == "THIS IS A NONCE")

    }

    "get same fields with a non-uuid id" in {
      val event =
        """
          |{
          |"headers":{"hola":["Hola"],"adios":["goodbye"]},
          |"id":"this is my id",
          |"customer_id": "61002dd0-23e7-11e9-8be0-61a26140e9b5",
          |"service_class":"com.ubirch.sdk.EventLogging",
          |"category":"My Category",
          |"event":{"name":"Hola"},
          |"event_time":"2019-01-29T17:00:28.333Z",
          |"signature":"THIS IS A SIGNATURE",
          |"nonce":"THIS IS A NONCE"
          |}""".stripMargin

      val fromJson = EventLogJsonSupport.FromString[EventLog](event).get

      assert(fromJson.headers == Headers.create("hola" -> "Hola", "adios" -> "goodbye"))
      assert(fromJson.id == "this is my id")
      assert(fromJson.customerId == "61002dd0-23e7-11e9-8be0-61a26140e9b5")
      assert(fromJson.serviceClass == "com.ubirch.sdk.EventLogging")
      assert(fromJson.category == "My Category")
      assert(fromJson.event == EventLogJsonSupport.getJValue("""{"name":"Hola"}"""))
      assert(fromJson.eventTime == JsonHelper.formats.dateFormat.parse("2019-01-29T17:00:28.333Z").getOrElse(new Date))
      assert(fromJson.signature == "THIS IS A SIGNATURE")
      assert(fromJson.nonce == "THIS IS A NONCE")

    }

    "have proper types" in {
      val event =
        """
          |{
          |"headers":{"hola":["Hola"],"adios":["Hello"]},
          |"id":"this is my id",
          |"customer_id": "61002dd0-23e7-11e9-8be0-61a26140e9b5",
          |"service_class":"com.ubirch.sdk.EventLogging",
          |"category":"My Category",
          |"event":{"name":"Hola"},
          |"event_time":"2019-01-29T17:00:28.333Z",
          |"signature":"THIS IS A SIGNATURE",
          |"nonce":"THIS IS A NONCE"
          |}
          |""".stripMargin

      val fromJson = EventLogJsonSupport.FromString[EventLog](event).get

      assert(fromJson.headers.isInstanceOf[Headers])
      assert(fromJson.id.isInstanceOf[String])
      assert(fromJson.id.isInstanceOf[String])
      assert(fromJson.serviceClass.isInstanceOf[String])
      assert(fromJson.category.isInstanceOf[String])
      assert(fromJson.event.isInstanceOf[JValue])
      assert(fromJson.eventTime.isInstanceOf[Date])
      assert(fromJson.signature.isInstanceOf[String])
      assert(fromJson.nonce.isInstanceOf[String])

    }

    "check constructors (1)" in {
      val data: JValue = parse(""" { "numbers" : [1, 2, 3, 4] } """)

      val el = EventLog(data)

      assert(el.id.isEmpty)
      assert(el.customerId.isEmpty)
      assert(el.serviceClass.isEmpty)
      assert(el.category.isEmpty)
      assert(el.event == data)
      assert(el.eventTime != null)
      assert(el.signature.isEmpty)
      assert(el.nonce.isEmpty)

    }

    "check constructors (2)" in {

      val data: JValue = parse(""" { "numbers" : [1, 2, 3, 4] } """)
      val time = new Date()

      val el = EventLog("my service class", "my category", data)

      assert(el.customerId.isEmpty)
      assert(el.serviceClass == "my service class")
      assert(el.category == "my category")
      assert(el.event == data)
      assert(el.eventTime == time)
      assert(el.signature.isEmpty)
      assert(el.nonce.isEmpty)

    }

    "check constructors (3)" in {

      val data: JValue = parse(""" { "numbers" : [1, 2, 3, 4] } """)

      val el = EventLog(
        "my customer id",
        "my service class",
        "my category",
        data
      )

      assert(el.id.nonEmpty)
      assert(el.customerId == "my customer id")
      assert(el.serviceClass == "my service class")
      assert(el.category == "my category")
      assert(el.event == data)
      assert(el.eventTime != null)
      assert(el.signature.isEmpty)
      assert(el.nonce.isEmpty)

    }

    "check constructors (4)" in {

      val data: JValue = parse(""" { "numbers" : [1, 2, 3, 4] } """)
      val time = new Date()

      val id = UUIDHelper.timeBasedUUID.toString

      val headers = Headers.create("HOLA" -> "HOLA")

      val lookupKeys = Seq(LookupKey("name", "category", "key", Seq("value")))

      val el = EventLog(
        headers,
        id,
        "my customer id",
        "my service class",
        "my category",
        data,
        time,
        "my signature",
        "my nonce",
        lookupKeys
      )

      assert(el.headers == headers)
      assert(el.id == id)
      assert(el.customerId == "my customer id")
      assert(el.serviceClass == "my service class")
      assert(el.category == "my category")
      assert(el.event == data)
      assert(el.eventTime == time)
      assert(el.signature == "my signature")
      assert(el.nonce == "my nonce")
      assert(el.lookupKeys == lookupKeys)
      assert(el.lookupKeys == Seq(LookupKey("name", "category", Key("key", None), Seq(Value("value", None)))))

    }

    "check withXXXX helpers" in {

      val data: JValue = parse(""" { "numbers" : [1, 2, 3, 4] } """)

      val date = new Date()

      val el = EventLog(data)
        .withNewId("my id")
        .withCustomerId("my customer id")
        .withCategory("my customer id")
        .withServiceClass("my service class")
        .withCategory("my category")
        .withEventTime(date)
        .withSignature("my signature")
        .withNonce("my nonce")
        .withHeaders("hola" -> "Hello", "hey" -> "como estas")

      assert(el.id == "my id")
      assert(el.customerId == "my customer id")
      assert(el.serviceClass == "my service class")
      assert(el.category == "my category")
      assert(el.event == data)
      assert(el.eventTime == date)
      assert(el.signature == "my signature")
      assert(el.nonce == "my nonce")
      assert(el.headers == Headers.create("hola" -> "Hello", "hey" -> "como estas"))

    }

    "check signature" in {

      import com.ubirch.models.EnrichedEventLog.enrichedEventLog

      val config = new ConfigProvider {} get ()

      val data: JValue = parse("""{ "numbers" : [1, 2, 3, 4] }""")

      val date = new Date()

      val el = EventLog(data)
        .withNewId("my id")
        .withCustomerId("my customer id")
        .withCategory("my customer id")
        .withServiceClass("my service class")
        .withCategory("my category")
        .withEventTime(date)
        .withNonce("my nonce")
        .sign(config)

      assert(el.id == "my id")
      assert(el.customerId == "my customer id")
      assert(el.serviceClass == "my service class")
      assert(el.category == "my category")
      assert(el.event == data)
      assert(el.eventTime == date)
      assert(el.signature == SigningHelper.signAndGetAsHex(config, SigningHelper.getBytesFromString(data.toString)))
      assert(el.nonce == "my nonce")

    }

    "check random nonce" in {

      val data: JValue = parse("""{ "numbers" : [1, 2, 3, 4] }""")

      val date = new Date()

      val el = EventLog(data)
        .withNewId("my id")
        .withCustomerId("my customer id")
        .withCategory("my customer id")
        .withServiceClass("my service class")
        .withCategory("my category")
        .withEventTime(date)
        .withSignature("my signature")
        .withRandomNonce

      assert(el.id == "my id")
      assert(el.customerId == "my customer id")
      assert(el.serviceClass == "my service class")
      assert(el.category == "my category")
      assert(el.event == data)
      assert(el.eventTime == date)
      assert(el.signature == "my signature")
      assert(el.signature == "my signature")
      assert(el.nonce != "")

    }

    "check trace header" in {
      val data: JValue = parse("""{ "numbers" : [1, 2, 3, 4] }""")

      val date = new Date()

      val el = EventLog(data)
        .withNewId("my id")
        .withCustomerId("my customer id")
        .withCategory("my customer id")
        .withServiceClass("my service class")
        .withCategory("my category")
        .withEventTime(date)
        .withSignature("my signature")
        .addTraceHeader("System ONE")
        .addTraceHeader("System TWO")

      println(el.toJson)

    }

  }

}
