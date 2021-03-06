package com.ubirch

import java.util.{ Date, UUID }

import com.ubirch.models.{ Error, EventLog, Headers }
import com.ubirch.util.{ EventLogJsonSupport, UUIDHelper }
import org.json4s.JValue
import org.json4s.jackson.JsonMethods.parse

object Entities extends UUIDHelper {

  object Events {

    val data: JValue = parse(""" { "numbers" : [1, 2, 3, 4] } """)

    def eventExample(id: UUID = randomUUID): EventLog = {
      val date = new Date()
      EventLog(
        Headers.empty,
        id,
        "this is customer id",
        "this is a service class",
        "this is a category",
        data,
        date,
        "this is a signature",
        "this is a nonce",
        Nil
      )
    }

    def eventExample(id: UUID, category: String): EventLog = {
      val date = new Date()
      EventLog(
        Headers.empty,
        id,
        "this is customer id",
        "this is a service class",
        category,
        data,
        date,
        "this is a signature",
        "this is a nonce",
        Nil
      )
    }

  }

  object Errors {

    def errorExample(id: UUID = randomUUID): Error = Error(
      id = id,
      message = "This is an error message",
      exceptionName = "My_Exception",
      value = "Are you serious?"
    )

    def errorExampleAsString(error: Error): String = EventLogJsonSupport.ToJson[Error](error).toString

  }

}
