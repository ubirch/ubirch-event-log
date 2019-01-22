package com.ubirch

import java.util.{ Date, UUID }

import com.ubirch.models.{ Error, Event, EventLog, TimeInfo }
import com.ubirch.util.{ ToJson, UUIDHelper }
import org.json4s.jackson.JsonMethods.parse
import com.ubirch.util.Implicits.enrichedDate

object Entities extends UUIDHelper {

  object Events {

    val data = parse(""" { "numbers" : [1, 2, 3, 4] } """)

    def eventExample(id: UUID = randomUUID) = {
      val date = new Date()
      EventLog(
        Event(
          id,
          "this is a service class",
          "this is a category",
          data,
          date,
          date.buildTimeInfo
        ),
        "this is a signature",
        date
      )
    }

    def eventExampleAsString(eventLog: EventLog) = ToJson[EventLog](eventLog).toString

  }

  object Errors {

    def errorExample(id: UUID = randomUUID) = Error(
      id = id,
      message = "This is an error message",
      exceptionName = "My_Exception",
      value = "Are you serious?"
    )

    def errorExampleAsString(error: Error) = ToJson[Error](error).toString

  }

}
