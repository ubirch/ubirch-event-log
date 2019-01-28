package com.ubirch.models

import java.util.{ Date, UUID }

import com.ubirch.util.ToJson
import com.ubirch.util.UUIDHelper._
import org.json4s.JValue

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

trait JValueEventLog extends EventLogBase[JValue]

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

