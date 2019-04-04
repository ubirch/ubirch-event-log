package com.ubirch.models

import java.util.{ Date, UUID }

import com.ubirch.util.EventLogJsonSupport
import com.ubirch.util.UUIDHelper._
import org.json4s.JValue

/**
  * EventLogBase that conforms a packet for the event T.
  *
  * @tparam T Represents the Type of the Event.
  */
trait EventLogBase[T] {

  val id: String
  val customerId: String
  val serviceClass: String
  val category: String
  val eventTime: Date
  val eventTimeInfo: TimeInfo
  val event: T
  val signature: String

  def withCategory(category: String): EventLogBase[T]

  def withServiceClass(serviceClass: String): EventLogBase[T]

  def withEventTime(eventTime: Date): EventLogBase[T]

  def withCurrentEventTime: EventLogBase[T]

  def withNewId: EventLogBase[T]

  def withNewId(id: UUID): EventLogBase[T]

  def withSignature(signature: String): EventLogBase[T]

}

/**
  * An EventLog whose Type T is JValue
  */
trait JValueEventLog extends EventLogBase[JValue]

/**
  * Concrete type for the EventLogBase whose type T is JValue
  *
  * @param id            String that identifies the EventLog. It can be a hash or a UUID or anything unique
  * @param customerId    Represents an id for a customer id.
  * @param serviceClass  Represents the name from where the log comes.
  *                     E.G: The name of the class.
  * @param category      Represents the category for the event. This is useful for
  *                      adding layers of description to the event.
  * @param event         Represents the event that is to be recorded.
  * @param eventTime     Represents the time when the event log was created.
  * @param eventTimeInfo Represents the time of the event in an unfolded manner.
  *                      This is useful and needed for making cluster keys with
  *                      the time of the event possible. Helpers are provided
  *                      to support its creation from the eventTime.
  * @param signature     Represents the signature for the event log.
  */
case class EventLog(id: String, customerId: String, serviceClass: String, category: String, event: JValue, eventTime: Date, eventTimeInfo: TimeInfo, signature: String) extends JValueEventLog {

  override def withNewId: EventLog = this.copy(id = timeBasedUUID.toString)

  override def withNewId(id: UUID): EventLog = this.copy(id = id.toString)

  def withCustomerId(customerId: String): EventLog = this.copy(customerId = customerId)

  override def withCategory(category: String): EventLog = this.copy(category = category)

  override def withServiceClass(serviceClass: String): EventLog = this.copy(serviceClass = serviceClass)

  override def withEventTime(eventTime: Date): EventLog = {
    this.copy(eventTime = eventTime, eventTimeInfo = TimeInfo.fromDate(eventTime))
  }

  override def withCurrentEventTime: EventLog = {
    val currentTime = new Date
    this.copy(eventTime = currentTime, eventTimeInfo = TimeInfo.fromDate(currentTime))
  }

  override def withSignature(signature: String): EventLog = this.copy(signature = signature)

  override def toString: String = {
    EventLogJsonSupport.ToJson[this.type](this).toString
  }
}

/**
  * Companion object that holds useful for things for the management of the EventLog
  */
object EventLog {

  def apply(serviceClass: String, category: String, event: JValue): EventLog = {
    val currentTime = new Date
    EventLog(timeBasedUUID, "", serviceClass, category, event, currentTime, TimeInfo.fromDate(currentTime), "")
  }

  def apply(customerId: String, serviceClass: String, category: String, event: JValue): EventLog = {
    val currentTime = new Date
    EventLog(timeBasedUUID, customerId, serviceClass, category, event, currentTime, TimeInfo.fromDate(currentTime), "")
  }

  def apply(id: UUID, customerId: String, serviceClass: String, category: String, event: JValue, eventTime: Date, eventTimeInfo: TimeInfo, signature: String): EventLog = {
    EventLog(id.toString, customerId, serviceClass, category, event, eventTime, eventTimeInfo, signature)
  }

}

