package com.ubirch.models

import java.util.{ Date, UUID }

import com.ubirch.util.ToJson
import com.ubirch.util.UUIDHelper._
import org.json4s.JValue

/**
  * EventLogBase that conforms a packet for the event T.
  * @tparam T Represents the Type of the Event.
  */
trait EventLogBase[T] {

  val id: UUID
  val serviceClass: String
  val category: String
  val eventTime: Date
  val eventTimeInfo: TimeInfo
  val event: T
  val signature: String

  def sign: EventLogBase[T]

  def withCategory(category: String): EventLogBase[T]
  def withServiceClass(serviceClass: String): EventLogBase[T]
  def withEventTime(eventTime: Date): EventLogBase[T]
  def withCurrentEventTime: EventLogBase[T]
  def withNewId: EventLogBase[T]
  def withNewId(id: UUID): EventLogBase[T]

}

/**
  * An EventLog whose Type T is JValue
  */
trait JValueEventLog extends EventLogBase[JValue]

/**
  * Concrete type for the EventLogBase whose type T is JValue
  * @param id UUID that identifies the EventLog
  * @param serviceClass Represents the name from where the log comes.
  *                     E.G: The name of the class.
  * @param category Represents the category for the event. This is useful for
  *                 adding layers of description to the event.
  * @param event Represents the event that is to be recorded.
  * @param eventTime Represents the time when the event log was created.
  * @param eventTimeInfo Represents the time of the event in an unfolded manner.
  *                      This is useful and needed for making cluster keys with
  *                      the time of the event possible. Helpers are provided
  *                      to support its creation from the eventTime.
  * @param signature Represents the signature for the event log.
  */
case class EventLog(
    id: UUID,
    serviceClass: String,
    category: String,
    event: JValue,
    eventTime: Date,
    eventTimeInfo: TimeInfo,
    signature: String
) extends JValueEventLog {

  override def sign: EventLog = this.copy(signature = "THIS IS A SIGNATURE")

  override def withNewId: EventLog = this.copy(id = timeBasedUUID)
  override def withNewId(id: UUID): EventLog = this.copy(id = id)
  override def withCategory(category: String): EventLog = this.copy(category = category)
  override def withServiceClass(serviceClass: String): EventLog = this.copy(serviceClass = serviceClass)
  override def withEventTime(eventTime: Date): EventLog = {
    this.copy(eventTime = eventTime, eventTimeInfo = TimeInfo(eventTime))
  }
  override def withCurrentEventTime: EventLog = {
    val currentTime = new Date
    this.copy(eventTime = currentTime, eventTimeInfo = TimeInfo(currentTime))
  }

  override def toString: String = {
    ToJson[this.type](this).toString
  }
}

/**
  * Companion object that holds useful for things for the management of the EventLog
  */
object EventLog {

  def apply(serviceClass: String, category: String, event: JValue): EventLog = {
    val currentTime = new Date
    EventLog(
      timeBasedUUID,
      serviceClass,
      category,
      event,
      currentTime,
      TimeInfo(currentTime),
      ""
    ).sign
  }

}

