package com.ubirch.services.kafka.consumer

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.{ EventLog, Events }
import com.ubirch.services.kafka.MessageEnvelope
import com.ubirch.util.Exceptions.ExecutionException
import com.ubirch.util.FromString
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.concurrent.{ ExecutionContext, Future }

trait Executor[-T1, +R] extends (T1 ⇒ R) {
  self ⇒
  override def apply(v1: T1): R

  def andThen[Q](other: Executor[R, Q]): Executor[T1, Q] = {
    v1: T1 ⇒ other(self(v1))
  }

}

class Wrapper extends Executor[ConsumerRecord[String, String], MessageEnvelope[String]] {

  override def apply(v1: ConsumerRecord[String, String]): MessageEnvelope[String] = {
    MessageEnvelope.fromRecord(v1)
  }

}

class FilterEmpty extends Executor[MessageEnvelope[String], MessageEnvelope[String]] {

  override def apply(v1: MessageEnvelope[String]): MessageEnvelope[String] = {

    if (v1.payload.nonEmpty) {
      v1
    } else {

      throw ExecutionException("Record is empty", ExecutionException.EmptyValue)
    }

  }

}

class EventLogParser
    extends Executor[MessageEnvelope[String], MessageEnvelope[EventLog]]
    with LazyLogging {

  override def apply(v1: MessageEnvelope[String]): MessageEnvelope[EventLog] = {

    val result: MessageEnvelope[EventLog] = try {
      v1.copy(payload = FromString[EventLog](v1.payload).get)
    } catch {
      case e: Exception ⇒
        logger.error("Error Parsing Event: " + e.getMessage)
        throw ExecutionException("Error Parsing Into Event Log", ExecutionException.ParsingIntoEventLog)
    }

    result

  }
}

class EventsStore @Inject() (events: Events)(implicit ec: ExecutionContext)
    extends Executor[MessageEnvelope[EventLog], Future[Unit]] {

  override def apply(v1: MessageEnvelope[EventLog]): Future[Unit] = {
    events.insert(v1.payload)
  }
}

trait ExecutorFamily {

  def wrapper: Wrapper

  def filterEmpty: FilterEmpty

  def eventsStore: EventsStore

  def eventLogParser: EventLogParser

}

case class DefaultExecutorFamily @Inject() (
  wrapper: Wrapper,
  filterEmpty: FilterEmpty,
  eventLogParser: EventLogParser,
  eventsStore: EventsStore) extends ExecutorFamily

class DefaultExecutor @Inject() (executorFamily: ExecutorFamily, events: Events) {

  import executorFamily._

  def composed = wrapper andThen filterEmpty andThen eventLogParser andThen eventsStore

  def executor = composed

}
