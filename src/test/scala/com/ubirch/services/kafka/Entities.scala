package com.ubirch.services.kafka

import java.util.{ Date, UUID }

import com.ubirch.models.{ Event, EventLog, TimeInfo }
import com.ubirch.util.ToJson
import org.json4s.jackson.JsonMethods.parse

object Entities {

  object Events {

    val data = parse(""" { "numbers" : [1, 2, 3, 4] } """)

    def eventExample(id: UUID = UUID.randomUUID()) = EventLog(
      Event(
        id,
        "this is a service class",
        "this is a category",
        data,
        new Date(),
        TimeInfo(2018, 12, 12, 12, 12, 12, 12)),
      "this is a signature",
      new Date(),
      new Date())

    def eventExampleAsString(eventLog: EventLog) = ToJson[EventLog](eventLog).toString

  }

}
