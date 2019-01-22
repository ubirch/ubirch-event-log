package com.ubirch.sdk.process

import com.ubirch.models.{ Event, EventLog }
import com.ubirch.process.Executor
import com.ubirch.util.ToJson
import org.json4s.JValue

class CreateEventFrom[T: Manifest](serviceClass: String, category: String) extends Executor[T, Event] {
  override def apply(v1: T): Event = Event(serviceClass, category, ToJson(v1).get)
}

class CreateEventFromJValue(serviceClass: String, category: String) extends Executor[JValue, Event] {
  override def apply(v1: JValue): Event = Event(serviceClass, category, v1)
}

class PackIntoEventLog extends Executor[Event, EventLog] {
  override def apply(v1: Event): EventLog = EventLog(v1)
}

class Logger extends Executor[EventLog, Unit] {
  override def apply(v1: EventLog): Unit = println(v1)
}

object DefaultClientExecutors {

  def fromJValue(serviceClass: String, category: String) = {
    new CreateEventFromJValue(serviceClass, category) andThen new PackIntoEventLog andThen new Logger
  }

  def from[T: Manifest](serviceClass: String, category: String) = {
    new CreateEventFrom[T](serviceClass, category) andThen new PackIntoEventLog andThen new Logger
  }

}
