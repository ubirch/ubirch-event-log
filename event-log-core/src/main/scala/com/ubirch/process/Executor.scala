package com.ubirch.process

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.{ Error, EventLog, Events }
import com.ubirch.services.kafka.MessageEnvelope
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.util.Exceptions._
import com.ubirch.util.{ FromString, UUIDHelper }
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.concurrent.{ ExecutionContext, Future }

trait Executor[-T1, +R] extends (T1 => R) {
  self =>
  override def apply(v1: T1): R

  def andThen[Q](other: Executor[R, Q]): Executor[T1, Q] = {
    v1: T1 => other(self(v1))
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
      throw EmptyValueException("Record is empty")
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
      case e: Exception =>
        logger.error("Error Parsing Event: " + e.getMessage)
        throw ParsingIntoEventLogException("Error Parsing Into Event Log", v1.payload)
    }

    result

  }
}

class EventsStore @Inject() (events: Events)(implicit ec: ExecutionContext)
  extends Executor[MessageEnvelope[EventLog], Future[Unit]]
  with LazyLogging {

  override def apply(v1: MessageEnvelope[EventLog]): Future[Unit] = {
    events.insert(v1.payload).recover {
      case e: Exception =>
        logger.error("Error storing data: " + e.getMessage)
        throw StoringIntoEventLogException("Error storing data", v1.payload, e.getMessage)
    }
  }
}

trait ExecutorFamily {

  def wrapper: Wrapper

  def filterEmpty: FilterEmpty

  def eventsStore: EventsStore

  def eventLogParser: EventLogParser

}

@Singleton
case class DefaultExecutorFamily @Inject() (
    wrapper: Wrapper,
    filterEmpty: FilterEmpty,
    eventLogParser: EventLogParser,
    eventsStore: EventsStore
) extends ExecutorFamily

@Singleton
class DefaultExecutor @Inject() (val reporter: Reporter, executorFamily: ExecutorFamily) {

  import executorFamily._
  import UUIDHelper._

  def composed = wrapper andThen filterEmpty andThen eventLogParser andThen eventsStore

  def executor = composed

  def executorExceptionHandler(exception: Exception): Unit = {
    import reporter.Types._

    val uuid = timeBasedUUID

    exception match {
      case e: EmptyValueException =>
        reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name))
      case e: ParsingIntoEventLogException =>
        reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = e.value))
      case e: StoringIntoEventLogException =>
        reporter.report(Error(id = e.eventLog.event.id, message = e.getMessage, exceptionName = e.name, value = e.eventLog.toString))
      case e: Exception =>
        reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.getClass.getCanonicalName))
    }

  }

}
