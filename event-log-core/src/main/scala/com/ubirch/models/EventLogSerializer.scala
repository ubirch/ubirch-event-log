package com.ubirch.models

import java.util.Date

import com.ubirch.util.EventLogJsonSupport._
import org.json4s.JsonDSL._
import org.json4s.{ CustomSerializer, JObject, JValue, MappingException }

import scala.util.control.NonFatal

/**
  * Object that contains all customized serializers
  */
object CustomSerializers {
  val all = List(new EventLogSerializer, new HeadersSerializer)
}

/**
  * Companion object that keeps the names of the fields
  * for the EventSerializer
  */
object EventLogSerializer {
  val HEADERS = "headers"
  val ID = "id"
  val CUSTOMER_ID = "customer_id"
  val SERVICE_CLASS = "service_class"
  val CATEGORY = "category"
  val EVENT = "event"
  val EVENT_TIME = "event_time"
  val SIGNATURE = "signature"
  val NONCE = "nonce"
  val LOOKUP_KEYS = "lookup_keys"
  val LOOKUP_KEYS_CATEGORY = "category"
  val LOOKUP_KEYS_KEY = "key"
  val LOOKUP_KEYS_VALUE = "value"

}

/**
  * Custom serializer for the EventLog type.
  * Useful to not stringify fields that are not needed in
  * the json format.
  */
class EventLogSerializer extends CustomSerializer[EventLog](_ =>
  ({
    case jsonObj: JObject =>
      import EventLogSerializer._
      try {

        val _jsonObj = jsonObj.underscoreKeys

        val headers: Headers = (_jsonObj \ HEADERS).extract[Headers]
        val id: String = (_jsonObj \ ID).extract[String]
        val customerId: String = (_jsonObj \ CUSTOMER_ID).extract[String]
        val serviceClass: String = (_jsonObj \ SERVICE_CLASS).extract[String]
        val category: String = (_jsonObj \ CATEGORY).extract[String]
        val event: JValue = _jsonObj \ EVENT
        val eventTime: Date = (_jsonObj \ EVENT_TIME).extract[Date]
        val signature: String = (_jsonObj \ SIGNATURE).extract[String]
        val nonce: String = (_jsonObj \ NONCE).extract[String]
        val lookupKeys: Seq[LookupKey] = (_jsonObj \ LOOKUP_KEYS).extract[Seq[LookupKey]]

        EventLog(headers, id, customerId, serviceClass, category, event, eventTime, signature, nonce, lookupKeys)

      } catch {
        case NonFatal(e) =>
          val message = s"For Dates, Use this format: ${formats.dateFormat.format(new Date)} - ${e.getMessage}"
          throw MappingException(message, new java.lang.IllegalArgumentException(e))

      }

  }, {

    case eventLog: EventLog =>
      import EventLogSerializer._

      (HEADERS -> eventLog.headers.toMap) ~
        (ID -> eventLog.id) ~
        (CUSTOMER_ID -> eventLog.customerId) ~
        (SERVICE_CLASS -> eventLog.serviceClass) ~
        (CATEGORY -> eventLog.category) ~
        (EVENT -> eventLog.event) ~
        (EVENT_TIME -> formats.dateFormat.format(eventLog.eventTime)) ~
        (SIGNATURE -> eventLog.signature) ~
        (NONCE -> eventLog.nonce) ~
        (LOOKUP_KEYS ->
          eventLog.lookupKeys.map { x =>
            (LOOKUP_KEYS_CATEGORY -> x.category) ~
              (LOOKUP_KEYS_KEY -> x.key) ~
              (LOOKUP_KEYS_VALUE -> x.value)
          })

  }))

class HeadersSerializer extends CustomSerializer[Headers](_ => (
  {
    case jsonObj: JObject =>

      try {
        val _jsonObj = jsonObj.underscoreKeys
        val headers: Map[String, Set[String]] = _jsonObj.extract[Map[String, Set[String]]]
        Headers.fromMap(headers)
      } catch {
        case NonFatal(e) =>
          throw MappingException(e.getMessage, new java.lang.IllegalArgumentException(e))

      }

  }, {
    case headers: Headers => headers.toMap
  }
))
