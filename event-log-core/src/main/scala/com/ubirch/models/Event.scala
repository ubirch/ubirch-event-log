package com.ubirch.models

import java.util.{ Date, UUID }

import com.ubirch.util.ToJson
import com.ubirch.util.UUIDHelper._
import io.getquill.Embedded
import org.json4s.JValue

trait EventBase {
  val id: UUID
  val serviceClass: String
  val category: String
  val eventTime: Date

  def withCategory(category: String): EventBase
  def withServiceClass(serviceClass: String): EventBase
  def withEventTime(eventTime: Date): EventBase
  def withCurrentEventTime: EventBase
  def withNewId: EventBase
  def withNewId(id: UUID): EventBase

}

trait EventMsgBase {

  val event: EventBase
  val signature: String

  def sign: EventMsgBase

  def withCategory(category: String): EventMsgBase
  def withServiceClass(serviceClass: String): EventMsgBase
  def withEventTime(eventTime: Date): EventMsgBase
  def withCurrentEventTime: EventMsgBase
  def withNewId: EventMsgBase
  def withNewId(id: UUID): EventMsgBase

}

case class Event(
    id: UUID,
    serviceClass: String,
    category: String,
    event: JValue,
    eventTime: Date,
    eventTimeInfo: TimeInfo
) extends Embedded with EventBase {

  def withNewId: Event = this.copy(id = timeBasedUUID)
  def withNewId(id: UUID): Event = this.copy(id = id)
  def withCategory(category: String): Event = this.copy(category = category)
  def withServiceClass(serviceClass: String): Event = this.copy(serviceClass = serviceClass)
  def withEventTime(eventTime: Date): Event = this.copy(eventTime = eventTime, eventTimeInfo = TimeInfo(eventTime))
  def withCurrentEventTime: Event = {
    val currentTime = new Date
    this.copy(eventTime = currentTime, eventTimeInfo = TimeInfo(currentTime))
  }

  override def toString: String = {
    ToJson[this.type](this).toString
  }

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

  def withEvent(event: Event): EventLog = this.copy(event = event)

  override def sign: EventLog = this.copy(signature = "THIS IS A SIGNATURE")

  //Events helper
  override def withNewId: EventLog = withEvent(this.event.withNewId(id = timeBasedUUID))
  override def withNewId(id: UUID): EventLog = withEvent(this.event.withNewId(id = id))
  override def withCategory(category: String): EventLog = withEvent(this.event.withCategory(category = category))
  override def withServiceClass(serviceClass: String): EventLog = withEvent(this.event.withServiceClass(serviceClass = serviceClass))
  override def withEventTime(eventTime: Date): EventLog = withEvent(this.event.withEventTime(eventTime))
  override def withCurrentEventTime: EventLog = withEvent(this.event.withCurrentEventTime)

  def id: UUID = event.id
  def eventTime: Date = event.eventTime
  def eventTimeInfo: TimeInfo = event.eventTimeInfo
  def category: String = event.category
  def serviceClass: String = event.serviceClass
  //

  override def toString: String = {
    ToJson[this.type](this).toString
  }
}

object EventLog {

  def apply(event: Event): EventLog = new EventLog(event, "", new Date).sign

}

