package com.ubirch

import java.util.{ Date, UUID }

import com.ubirch.models.{ Error, EventLog }
import com.ubirch.util.Implicits.enrichedDate
import com.ubirch.util.{ EventLogJsonSupport, UUIDHelper }
import org.json4s.jackson.JsonMethods.parse

object Entities extends UUIDHelper {

  object Events {

    val data = parse(""" { "numbers" : [1, 2, 3, 4] } """)

    def eventExample(id: UUID = randomUUID): EventLog = {
      val date = new Date()
      EventLog(
        id,
        "this is a service class",
        "this is a category",
        data,
        date,
        date.buildTimeInfo,
        "this is a signature"
      )
    }

  }

  object Errors {

    def errorExample(id: UUID = randomUUID) = Error(
      id = id,
      message = "This is an error message",
      exceptionName = "My_Exception",
      value = "Are you serious?"
    )

    def errorExampleAsString(error: Error): String = EventLogJsonSupport.ToJson[Error](error).toString

  }

}
