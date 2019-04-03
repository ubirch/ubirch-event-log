package com.ubirch.models

import java.util.{ Date, UUID }

import com.ubirch.util.EventLogJsonSupport._
import org.json4s.JsonDSL._
import org.json4s.{ CustomSerializer, JObject, JValue, MappingException }

import scala.util.control.NonFatal

/**
  * Object that contains all customized serializers
  */
object CustomSerializers {
  val all = List(new EventSerializer)
}

/**
  * Companion object that keeps the names of the fields
  * for the EventSerializer
  */
object EventSerializer {
  val ID = "id"
  val SERVICE_CLASS = "service_class"
  val CATEGORY = "category"
  val EVENT = "event"
  val EVENT_TIME = "event_time"
  val SIGNATURE = "signature"
}

/**
  * Custom serializer for the EventLog type.
  * Useful to not stringify fields that are not needed in
  * the json format.
  */
class EventSerializer extends CustomSerializer[EventLog](_ =>
  ({
    case jsonObj: JObject =>
      import EventSerializer._
      try {

        val _jsonObj = jsonObj.underscoreKeys

        val id: UUID = (_jsonObj \ ID).extract[UUID]
        val serviceClass: String = (_jsonObj \ SERVICE_CLASS).extract[String]
        val category: String = (_jsonObj \ CATEGORY).extract[String]
        val event: JValue = _jsonObj \ EVENT
        val eventTime: Date = (_jsonObj \ EVENT_TIME).extract[Date]
        val eventTimeInfo: TimeInfo = TimeInfo.fromDate(eventTime)
        val signature: String = (_jsonObj \ SIGNATURE).extract[String]

        EventLog(id, serviceClass, category, event, eventTime, eventTimeInfo, signature)
      } catch {
        case NonFatal(e) =>
          val t = "For Dates, Use this format: " + formats.dateFormat.format(new Date)
          throw MappingException(t + e.getMessage, new java.lang.IllegalArgumentException(e))

      }

  }, {

    case eventLog: EventLog =>
      import EventSerializer._

      (ID -> eventLog.id.toString) ~
        (SERVICE_CLASS -> eventLog.serviceClass) ~
        (CATEGORY -> eventLog.category) ~
        (EVENT -> eventLog.event) ~
        (EVENT_TIME -> formats.dateFormat.format(eventLog.eventTime)) ~
        (SIGNATURE -> eventLog.signature)

  }))
