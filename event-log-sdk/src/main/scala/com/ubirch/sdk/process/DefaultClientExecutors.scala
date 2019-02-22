package com.ubirch.sdk.process

import java.util.concurrent.{ Future => JavaFuture }

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.EventLog
import com.ubirch.process.Executor
import com.ubirch.sdk.ConfPaths
import com.ubirch.sdk.util.Exceptions._
import com.ubirch.services.kafka.producer.StringProducer
import com.ubirch.util.Implicits.enrichedConfig
import com.ubirch.util.{ FutureHelper, ProducerRecordHelper, ToJson }
import org.apache.kafka.clients.producer.{ ProducerRecord, RecordMetadata }
import org.json4s.JValue

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

/**
  * Executor for creating an Event from a value T.
  * @param serviceClass Represents the origin class for the event.
  * @param category Represents a category for the event.
  * @param manifest$T Represents the manifest for the type T the Event Type
  *                   wants to be created from.
  * @tparam T Represent the type from which an Event is created from.
  */
class CreateEventFrom[T: Manifest](serviceClass: String, category: String) extends Executor[T, EventLog] {
  override def apply(v1: T): EventLog = {
    try {
      val json = ToJson[T](v1)
      EventLog(serviceClass, category, json.get)
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
class CreateEventFromJValue(serviceClass: String, category: String) extends Executor[JValue, EventLog] {
  override def apply(v1: JValue): EventLog = EventLog(serviceClass, category, v1)
}

/**
  * Executor that creates a producer records and packages it in a tuple with the event log.
  * @param config Config object to read config keys from.
  */

class CreateProducerRecord(config: Config)
  extends Executor[EventLog, (ProducerRecord[String, String], EventLog)] {
  override def apply(v1: EventLog): (ProducerRecord[String, String], EventLog) = {

    try {

      val topic = config.getStringAsOption(ConfPaths.Producer.TOPIC_PATH).getOrElse("com.ubirch.eventlog")

      val json = ToJson[EventLog](v1)

      (ProducerRecordHelper.toRecord(topic, v1.id.toString, json.toString, Map.empty), v1)

    } catch {

      case e: Exception =>
        throw CreateProducerRecordException(v1, e.getMessage)

    }
  }
}

/**
  * Executor that sends a ProducerRecord to a Kafka producer and returns a
  * tuple of the java future returned by the client and the eventLog
  * @param stringProducer A string producer to send the json string of the EventLog
  */
class Commit(stringProducer: StringProducer) extends Executor[(ProducerRecord[String, String], EventLog), (JavaFuture[RecordMetadata], EventLog)] {

  def commit(record: ProducerRecord[String, String]): JavaFuture[RecordMetadata] = stringProducer.producer.send(record)

  override def apply(v1: (ProducerRecord[String, String], EventLog)): (JavaFuture[RecordMetadata], EventLog) = {

    val (record, eventLog) = v1

    try {

      (commit(record), eventLog)

    } catch {

      case e: Exception =>
        throw CommitException(eventLog, e.getMessage)

    }
  }
}

/**
  * Executor that synchronously handles the java future of the RecordMetadata.
  */
class CommitHandlerSync extends Executor[(JavaFuture[RecordMetadata], EventLog), EventLog] {

  def get(javaFutureRecordMetadata: JavaFuture[RecordMetadata]): RecordMetadata = javaFutureRecordMetadata.get()

  override def apply(v1: (JavaFuture[RecordMetadata], EventLog)): EventLog = {

    val (javaFutureRecordMetadata, eventLog) = v1

    try {

      get(javaFutureRecordMetadata)

      eventLog

    } catch {

      case e: Exception =>
        throw CommitHandlerSyncException(eventLog, e.getMessage)

    }
  }

}

/**
  * Executor that Asynchronously handles the java future of the RecordMetadata.
  */
class CommitHandlerAsync(implicit ec: ExecutionContext) extends Executor[(JavaFuture[RecordMetadata], EventLog), Future[EventLog]] {

  def get(javaFutureRecordMetadata: JavaFuture[RecordMetadata], eventLog: EventLog): Future[EventLog] = {
    FutureHelper.fromJavaFuture(javaFutureRecordMetadata).map { _ =>
      eventLog
    }.recover {
      case e: Exception =>
        throw CommitHandlerASyncException(eventLog, e.getMessage)
    }

  }

  override def apply(v1: (JavaFuture[RecordMetadata], EventLog)): Future[EventLog] = {

    val (javaFutureRecordMetadata, eventLog) = v1

    try {

      get(javaFutureRecordMetadata, eventLog)

    } catch {

      case e: Exception =>
        throw CommitHandlerASyncException(eventLog, e.getMessage)

    }

  }

}

/**
  * Executor that Asynchronously handles the java future of the RecordMetadata.
  * This mode is "fire and forget" type of response management.
  * This means that the java future returned is not treated.
  * This mode could be the fastest but may result in silence of the possible error.
  * One way to mitigate this is by adding a retry strategy to the producer.
  */
class CommitHandlerStealthAsync extends Executor[(JavaFuture[RecordMetadata], EventLog), EventLog] {

  override def apply(v1: (JavaFuture[RecordMetadata], EventLog)): EventLog = {

    val (_, eventLog) = v1

    try {

      eventLog

    } catch {

      case e: Exception =>
        throw CommitHandlerStealthAsyncException(eventLog, e.getMessage)

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

/**
  * Executor that logs Future[EventLogs]
  */

class FutureLogger(implicit ec: ExecutionContext) extends Executor[Future[EventLog], Future[EventLog]] with LazyLogging {
  override def apply(v1: Future[EventLog]): Future[EventLog] = {

    v1.onComplete {
      case Success(value) => logger.debug(value.toString)
      case Failure(exception) => logger.debug(exception.getMessage)
    }

    v1
  }
}
