package com.ubirch.models

import java.util.{ Date, UUID }

import io.getquill.Embedded
import org.json4s.JValue
import com.ubirch.util.UUIDHelper._

trait EventBase {
  val id: UUID
  val serviceClass: String
  val category: String
  val eventTime: Date

  def withCategory(category: String): EventBase
  def withServiceClass(serviceClass: String): EventBase
  def withEventTime(eventTime: Date): EventBase
  def withNewId(id: UUID): EventBase

}

trait EventMsgBase {
  val event: EventBase
  val signature: String

  def withEvent: EventMsgBase
  def sign: EventMsgBase

}

case class Event(
    id: UUID,
    serviceClass: String,
    category: String,
    event: JValue,
    eventTime: Date,
    eventTimeInfo: TimeInfo
) extends Embedded with EventBase {

  def withNewId(id: UUID): Event = this.copy(id = timeBasedUUID)
  def withCategory(category: String): Event = this.copy(category = category)
  def withServiceClass(serviceClass: String): Event = this.copy(serviceClass = serviceClass)
  def withEventTime(eventTime: Date): Event = this.copy(eventTime = eventTime, eventTimeInfo = TimeInfo(eventTime))

}

object Event {

  def apply(id: UUID, serviceClass: String, category: String, event: JValue, eventTime: Date): Event = {
    Event(
      id,
      serviceClass,
      category,
      event,
      eventTime,
      TimeInfo(eventTime)
    )
  }

  def apply(serviceClass: String, category: String, event: JValue, eventTime: Date): Event = {
    Event(
      timeBasedUUID,
      serviceClass,
      category,
      event,
      eventTime,
      TimeInfo(eventTime)
    )
  }

  def apply(serviceClass: String, category: String, event: JValue): Event = {
    val currentTime = new Date
    Event(
      timeBasedUUID,
      serviceClass,
      category,
      event,
      currentTime,
      TimeInfo(currentTime)
    )
  }

}

//TODO check whether we could use org.joda.time.DateTime instead of Date
case class EventLog(event: Event, signature: String, created: Date) extends EventMsgBase {

  override def withEvent: EventLog = copy(event = event)

  override def sign: EventLog = copy(signature = "THIS IS A SIGNATURE")

}

object EventLog {

  def apply(event: Event): EventLog = new EventLog(event, "", new Date).sign

}

