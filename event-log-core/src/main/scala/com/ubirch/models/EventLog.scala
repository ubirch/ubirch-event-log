package com.ubirch.models

import java.util.{ Date, UUID }

import com.ubirch.util.UUIDHelper._
import com.ubirch.util.{ EventLogJsonSupport, SigningHelper, UUIDHelper }
import org.json4s.JValue

/**
  * EventLogBase that conforms a packet for the event T.
  *
  * @tparam T Represents the Type of the Event.
  */
trait EventLogBase[T] {

  val headers: Headers
  val id: String
  val customerId: String
  val serviceClass: String
  val category: String
  val eventTime: Date
  val event: T
  val signature: String
  val nonce: String
  val lookupKeys: Seq[LookupKey]

  def withHeaders(headers: (String, String)*): EventLog

  def addHeaders(headers: (String, String)*): EventLog

  def addHeadersIf(predicate: => Boolean, headers: (String, String)*): EventLog

  def removeHeader(keys: String*): EventLog

  def replaceHeaders(headers: (String, String)*): EventLog

  def removeHeaders: EventLog

  def withCustomerId(customerId: String): EventLog

  def withCustomerId(customerId: UUID): EventLog

  def withIdAsCustomerId: EventLog

  def withCategory(category: String): EventLogBase[T]

  def withServiceClass(serviceClass: String): EventLogBase[T]

  def withEventTime(eventTime: Date): EventLogBase[T]

  def withCurrentEventTime: EventLogBase[T]

  def withNewId: EventLogBase[T]

  def withNewId(id: UUID): EventLogBase[T]

  def withNewId(id: String): EventLogBase[T]

  def withSignature(signature: String): EventLogBase[T]

  def withNonce(nonce: String): EventLogBase[T]

  def withRandomNonce: EventLogBase[T]

  def withLookupKeys(lookupKeys: Seq[LookupKey]): EventLog

  def addLookupKeys(lookupKey: LookupKey*): EventLog

}

/**
  * An EventLog whose Type T is JValue
  */
trait JValueEventLog extends EventLogBase[JValue]

/**
  * Concrete type for the EventLogBase whose type T is JValue
  * @param headers Represents a data structure that can contain key-value properties
  * @param id            String that identifies the EventLog. It can be a hash or a UUID or anything unique
  * @param customerId    Represents an id for a customer id.
  * @param serviceClass  Represents the name from where the log comes.
  *                     E.G: The name of the class.
  * @param category      Represents the category for the event. This is useful for
  *                      adding layers of description to the event.
  * @param event         Represents the event that is to be recorded.
  * @param eventTime     Represents the time when the event log was created.
  * @param signature     Represents the signature for the event log.
  * @param nonce         Represents a value that can be used to calculate the hash of the event.
  */
case class EventLog(
    headers: Headers,
    id: String,
    customerId: String,
    serviceClass: String,
    category: String,
    event: JValue,
    eventTime: Date,
    signature: String,
    nonce: String,
    lookupKeys: Seq[LookupKey]
) extends JValueEventLog {

  override def withHeaders(headers: (String, String)*): EventLog = this.copy(headers = Headers.create(headers: _*))

  override def addHeaders(headers: (String, String)*): EventLog = this.copy(headers = this.headers.add(headers: _*))

  override def addHeadersIf(predicate: => Boolean, headers: (String, String)*): EventLog = this.copy(headers = this.headers.add(headers: _*))

  override def removeHeader(keys: String*): EventLog = copy(headers = this.headers.remove(keys: _*))

  override def replaceHeaders(headers: (String, String)*): EventLog = this.copy(headers = this.headers.replace(headers: _*))

  override def removeHeaders: EventLog = this.copy(headers = Headers.empty)

  override def withNewId: EventLog = this.copy(id = timeBasedUUID.toString)

  override def withNewId(id: UUID): EventLog = this.copy(id = id.toString)

  override def withNewId(id: String): EventLog = this.copy(id = id)

  override def withCustomerId(customerId: String): EventLog = this.copy(customerId = customerId)

  override def withIdAsCustomerId: EventLog = this.copy(customerId = this.id)

  override def withCustomerId(customerId: UUID): EventLog = this.copy(customerId = customerId.toString)

  override def withCategory(category: String): EventLog = this.copy(category = category)

  override def withServiceClass(serviceClass: String): EventLog = this.copy(serviceClass = serviceClass)

  override def withEventTime(eventTime: Date): EventLog = this.copy(eventTime = eventTime)

  override def withCurrentEventTime: EventLog = this.copy(eventTime = new Date)

  override def withSignature(signature: String): EventLog = this.copy(signature = signature)

  override def withNonce(nonce: String): EventLog = this.copy(nonce = nonce)

  override def withRandomNonce: EventLog = {
    val randomValue: String = SigningHelper.bytesToHex(
      SigningHelper.getBytesFromString(
        UUIDHelper.randomUUID.toString
      )
    )
    this.copy(nonce = randomValue)
  }

  override def withLookupKeys(lookupKeys: Seq[LookupKey]): EventLog = this.copy(lookupKeys = lookupKeys)

  override def addLookupKeys(lookupKey: LookupKey*): EventLog = this.copy(lookupKeys = this.lookupKeys ++ lookupKey)

  def toJson: String = {
    EventLogJsonSupport.ToJson[this.type](this).toString
  }
}

/**
  * Companion object that holds useful for things for the management of the EventLog
  */
object EventLog {

  def apply(event: JValue): EventLog = {
    val currentTime = new Date
    EventLog(
      headers = Headers.empty,
      id = "",
      customerId = "",
      serviceClass = "",
      category = "",
      event = event,
      eventTime = currentTime,
      signature = "",
      nonce = "",
      lookupKeys = Nil
    )
  }

  def apply(serviceClass: String, category: String, event: JValue): EventLog = {
    val currentTime = new Date
    EventLog(
      headers = Headers.empty,
      id = timeBasedUUID,
      customerId = "",
      serviceClass = serviceClass,
      category = category,
      event = event,
      eventTime = currentTime,
      signature = "",
      nonce = "",
      lookupKeys = Nil
    )
  }

  def apply(customerId: String, serviceClass: String, category: String, event: JValue): EventLog = {
    val currentTime = new Date
    EventLog(
      headers = Headers.empty,
      id = timeBasedUUID,
      customerId = customerId,
      serviceClass = serviceClass,
      category = category,
      event = event,
      eventTime = currentTime,
      signature = "",
      nonce = "",
      lookupKeys = Nil
    )
  }

  def apply(
      headers: Headers,
      id: UUID,
      customerId: String,
      serviceClass: String,
      category: String,
      event: JValue,
      eventTime: Date,
      signature: String,
      nonce: String,
      lookupKeys: Seq[LookupKey]
  ): EventLog = {
    EventLog(
      headers = headers,
      id = id.toString,
      customerId = customerId,
      serviceClass = serviceClass,
      category = category,
      event = event,
      eventTime = eventTime,
      signature = signature,
      nonce = nonce,
      lookupKeys = lookupKeys
    )
  }

}

