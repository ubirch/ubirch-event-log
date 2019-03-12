package com.ubirch.models

import java.text.SimpleDateFormat
import java.util.Date

import com.ubirch.util.{ EventLogJsonSupport, JsonHelper, UUIDHelper }
import com.ubirch.{ Entities, TestBase }
import org.json4s.MappingException
import org.scalatest.mockito.MockitoSugar

class EventLogSpec extends TestBase with MockitoSugar {

  "Event Log Model" must {

    "generate new uuid" in {

      val uuid = UUIDHelper.timeBasedUUID
      val eventLog = Entities.Events.eventExample()

      assert(uuid == eventLog.withNewId(uuid).id)

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

      assert(TimeInfo(newDate) == newEventLog.eventTimeInfo)

      Thread.sleep(1000)

      assert(eventLog.eventTime.getTime < newEventLog.withCurrentEventTime.eventTime.getTime)

    }

    "have toString to Json" in {
      val eventLog = Entities.Events.eventExample()

      val eventLogAsString = eventLog.toString

      assert(eventLog.toString == eventLogAsString)
    }

    "convert string to type" in {
      val event = """{"id":"243f7063-6126-470e-9947-be49a62351c0","service_class":"this is a service class","category":"this is a category","event":{"numbers":[1,2,3,4]},"event_time":"Mon Jan 28 22:07:52 CET 2019","signature":"this is a signature"}"""

      val fromJson = EventLogJsonSupport.FromString[EventLog](event)

      assertThrows[MappingException](event == fromJson.get.toString)
    }

    "get same fields" in {
      val event = """{"id":"61002dd0-23e7-11e9-8be0-61a26140e9b5","service_class":"com.ubirch.sdk.EventLogging","category":"My Category","event":{"name":"Hola"},"event_time":"2019-01-29T17:00:28.333Z","signature":"THIS IS A SIGNATURE"}"""

      val fromJson = EventLogJsonSupport.FromString[EventLog](event).get

      assert(fromJson.id.toString == "61002dd0-23e7-11e9-8be0-61a26140e9b5")
      assert(fromJson.serviceClass.toString == "com.ubirch.sdk.EventLogging")
      assert(fromJson.category.toString == "My Category")
      assert(fromJson.event == EventLogJsonSupport.getJValue("""{"name":"Hola"}"""))
      assert(fromJson.eventTime == JsonHelper.formats.dateFormat.parse("2019-01-29T17:00:28.333Z").getOrElse(new Date))
      assert(fromJson.signature == "THIS IS A SIGNATURE")

    }

  }

}
