package com.ubirch.sdk.process

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.{ Event, EventLog }
import com.ubirch.process.Executor
import com.ubirch.sdk.util.Exceptions.{ CommitException, CreateEventFromException }
import com.ubirch.services.kafka.producer.StringProducer
import com.ubirch.util.Implicits.enrichedConfig
import com.ubirch.util.{ ProducerRecordHelper, ToJson }
import org.json4s.JValue

/**
  * Executor for creating an Event from a value T.
  * @param serviceClass Represents the origin class for the event.
  * @param category Represents a category for the event.
  * @param manifest$T Represents the manifest for the type T the Event Type
  *                   wants to be created from.
  * @tparam T Represent the type from which an Event is created from.
  */
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

/**
  * Executor for creating an Event from a JValue.
  * @param serviceClass Represents the origin class for the event.
  * @param category Represents a category for the event.
  */
class CreateEventFromJValue(serviceClass: String, category: String) extends Executor[JValue, Event] {
  override def apply(v1: JValue): Event = Event(serviceClass, category, v1)
}

/**
  * Executor to pack an Event into an EventLog
  */
class PackIntoEventLog extends Executor[Event, EventLog] {
  override def apply(v1: Event): EventLog = EventLog(v1)
}

/**
  * Executor that send an EventLog to a Kafka producer.
  * @param stringProducer A string producer to send the json string of the EventLog
  * @param config Config object to read config keys from.
  */

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

/**
  * Executor that logs EventLogs
  */

class Logger extends Executor[EventLog, EventLog] with LazyLogging {
  override def apply(v1: EventLog): EventLog = {
    logger.debug(v1.toString); v1
  }
}
