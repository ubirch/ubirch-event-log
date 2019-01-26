package com.ubirch.models

import java.text.SimpleDateFormat

import com.ubirch.util.{ ToJson, UUIDHelper }
import com.ubirch.{ Entities, TestBase }
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

      assert(eventLog.eventTime.getTime < newEventLog.withCurrentEventTime.eventTime.getTime)

    }

    "have toString to Json" in {
      val eventLog = Entities.Events.eventExample()

      val eventLogAsString = Entities.Events.eventExampleAsString(eventLog)

      assert(eventLog.event.toString == ToJson(eventLog.event).toString)

      assert(eventLog.toString == eventLogAsString)
    }

    "getters" in {

      val eventLog = Entities.Events.eventExample()

      assert(eventLog.id === eventLog.event.id)
      assert(eventLog.category === eventLog.event.category)
      assert(eventLog.serviceClass === eventLog.event.serviceClass)
      assert(eventLog.eventTime === eventLog.event.eventTime)
      assert(eventLog.eventTimeInfo === eventLog.event.eventTimeInfo)

    }

  }

}
