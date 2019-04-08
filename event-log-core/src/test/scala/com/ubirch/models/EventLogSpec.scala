package com.ubirch.models

import java.text.SimpleDateFormat
import java.util.Date

import com.ubirch.util.{ EventLogJsonSupport, JsonHelper, UUIDHelper }
import com.ubirch.{ Entities, TestBase }
import org.json4s.jackson.JsonMethods.parse
import org.json4s.{ JValue, MappingException }
import org.scalatest.mockito.MockitoSugar

class EventLogSpec extends TestBase with MockitoSugar {

  "Event Log Model" must {

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

      assert(TimeInfo.fromDate(newDate) == newEventLog.eventTimeInfo)

      Thread.sleep(1000)

      assert(eventLog.eventTime.getTime < newEventLog.withCurrentEventTime.eventTime.getTime)

    }

    "have toString to Json" in {
      val eventLog = Entities.Events.eventExample()

      val eventLogAsString = eventLog.toString

      assert(eventLog.toString == eventLogAsString)
    }

    "fail convert string to type when date format is wrong" in {
      val event = """{"id":"243f7063-6126-470e-9947-be49a62351c0","service_class":"this is a service class","category":"this is a category","event":{"numbers":[1,2,3,4]},"event_time":"Mon Jan 28 22:07:52 CET 2019","signature":"this is a signature"}"""

      val fromJson = EventLogJsonSupport.FromString[EventLog](event)

      assertThrows[MappingException](fromJson.get.toString)
    }

    "get same fields with a uuid id" in {
      val event = """{"id":"61002dd0-23e7-11e9-8be0-61a26140e9b5", "customer_id": "61002dd0-23e7-11e9-8be0-61a26140e9b5", "service_class":"com.ubirch.sdk.EventLogging","category":"My Category","event":{"name":"Hola"},"event_time":"2019-01-29T17:00:28.333Z","signature":"THIS IS A SIGNATURE"}"""

      val fromJson = EventLogJsonSupport.FromString[EventLog](event).get

      assert(fromJson.id == "61002dd0-23e7-11e9-8be0-61a26140e9b5")
      assert(fromJson.customerId == "61002dd0-23e7-11e9-8be0-61a26140e9b5")
      assert(fromJson.serviceClass == "com.ubirch.sdk.EventLogging")
      assert(fromJson.category == "My Category")
      assert(fromJson.event == EventLogJsonSupport.getJValue("""{"name":"Hola"}"""))
      assert(fromJson.eventTime == JsonHelper.formats.dateFormat.parse("2019-01-29T17:00:28.333Z").getOrElse(new Date))
      assert(fromJson.signature == "THIS IS A SIGNATURE")

    }

    "get same fields with a non-uuid id" in {
      val event = """{"id":"this is my id", "customer_id": "61002dd0-23e7-11e9-8be0-61a26140e9b5", "service_class":"com.ubirch.sdk.EventLogging","category":"My Category","event":{"name":"Hola"},"event_time":"2019-01-29T17:00:28.333Z","signature":"THIS IS A SIGNATURE"}"""

      val fromJson = EventLogJsonSupport.FromString[EventLog](event).get

      assert(fromJson.id == "this is my id")
      assert(fromJson.customerId == "61002dd0-23e7-11e9-8be0-61a26140e9b5")
      assert(fromJson.serviceClass == "com.ubirch.sdk.EventLogging")
      assert(fromJson.category == "My Category")
      assert(fromJson.event == EventLogJsonSupport.getJValue("""{"name":"Hola"}"""))
      assert(fromJson.eventTime == JsonHelper.formats.dateFormat.parse("2019-01-29T17:00:28.333Z").getOrElse(new Date))
      assert(fromJson.signature == "THIS IS A SIGNATURE")

    }

    "have proper types" in {
      val event = """{"id":"this is my id", "customer_id": "61002dd0-23e7-11e9-8be0-61a26140e9b5", "service_class":"com.ubirch.sdk.EventLogging","category":"My Category","event":{"name":"Hola"},"event_time":"2019-01-29T17:00:28.333Z","signature":"THIS IS A SIGNATURE"}"""

      val fromJson = EventLogJsonSupport.FromString[EventLog](event).get

      assert(fromJson.id.isInstanceOf[String])
      assert(fromJson.id.isInstanceOf[String])
      assert(fromJson.serviceClass.isInstanceOf[String])
      assert(fromJson.category.isInstanceOf[String])
      assert(fromJson.event.isInstanceOf[JValue])
      assert(fromJson.eventTime.isInstanceOf[Date])
      assert(fromJson.signature.isInstanceOf[String])

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
      assert(el.eventTimeInfo != null)
      assert(el.signature.isEmpty)

    }

    "check constructors (2)" in {

      val data: JValue = parse(""" { "numbers" : [1, 2, 3, 4] } """)
      val time = new Date()

      val id = UUIDHelper.timeBasedUUID

      val el = EventLog(
        id,
        "my customer id",
        "my service class",
        "my category",
        data,
        time,
        TimeInfo.fromDate(time),
        "my signature"
      )

      assert(el.id == id.toString)
      assert(el.customerId == "my customer id")
      assert(el.serviceClass == "my service class")
      assert(el.category == "my category")
      assert(el.event == data)
      assert(el.eventTime == time)
      assert(el.eventTimeInfo == TimeInfo.fromDate(time))
      assert(el.signature == "my signature")

    }

    "check constructors (3)" in {

      val data: JValue = parse(""" { "numbers" : [1, 2, 3, 4] } """)
      val time = new Date()

      val id = UUIDHelper.timeBasedUUID.toString

      val el = EventLog(
        id,
        "my customer id",
        "my service class",
        "my category",
        data,
        time,
        TimeInfo.fromDate(time),
        "my signature"
      )

      assert(el.id == id)
      assert(el.customerId == "my customer id")
      assert(el.serviceClass == "my service class")
      assert(el.category == "my category")
      assert(el.event == data)
      assert(el.eventTime == time)
      assert(el.eventTimeInfo == TimeInfo.fromDate(time))
      assert(el.signature == "my signature")

    }

    "check constructors (4)" in {

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
      assert(el.eventTimeInfo == TimeInfo.fromDate(el.eventTime))
      assert(el.signature.isEmpty)

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

      assert(el.id == "my id")
      assert(el.customerId == "my customer id")
      assert(el.serviceClass == "my service class")
      assert(el.category == "my category")
      assert(el.event == data)
      assert(el.eventTime == date)
      assert(el.eventTimeInfo == TimeInfo.fromDate(el.eventTime))
      assert(el.signature == "my signature")

    }

  }

}
