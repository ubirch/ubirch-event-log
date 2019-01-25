package com.ubirch.sdk.process

import com.typesafe.config.Config
import com.ubirch.models.{ Event, EventLog }
import com.ubirch.process.Executor
import com.ubirch.sdk.util.Exceptions.{ CommitException, CreateEventFromException }
import com.ubirch.services.kafka.producer.StringProducer
import com.ubirch.util.Implicits.enrichedConfig
import com.ubirch.util.{ ProducerRecordHelper, ToJson }
import org.json4s.JValue

class CreateEventFrom[T: Manifest](serviceClass: String, category: String) extends Executor[T, Event] {
  override def apply(v1: T): Event = {
    try {
      val json = ToJson[T](v1)
      Event(serviceClass, category, json.get)
    } catch {
      case e: Exception =>
        throw CreateEventFromException(v1, e.getMessage)
    }

  }
}

class CreateEventFromJValue(serviceClass: String, category: String) extends Executor[JValue, Event] {
  override def apply(v1: JValue): Event = Event(serviceClass, category, v1)
}

class PackIntoEventLog extends Executor[Event, EventLog] {
  override def apply(v1: Event): EventLog = EventLog(v1)
}

class Commit(stringProducer: StringProducer, config: Config) extends Executor[EventLog, EventLog] {
  override def apply(v1: EventLog): EventLog = {

    try {

      val topic = config.getStringAsOption("kafkaClient.topic").getOrElse("com.ubirch.eventlog")

      val json = ToJson[EventLog](v1)

      val record = ProducerRecordHelper.toRecord(topic, v1.event.id.toString, json.toString, Map.empty)
      stringProducer.producer.send(record)

      v1

    } catch {

      case e: Exception =>
        throw CommitException(v1, e.getMessage)

    }
  }
}

class Logger extends Executor[EventLog, EventLog] {
  override def apply(v1: EventLog): EventLog = {
    println(v1); v1
  }
}
