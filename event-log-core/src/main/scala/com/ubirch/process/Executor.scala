package com.ubirch.process

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.{ Error, EventLog, Events }
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

class FilterEmpty extends Executor[ConsumerRecord[String, String], ConsumerRecord[String, String]] {

  override def apply(v1: ConsumerRecord[String, String]): ConsumerRecord[String, String] = {
    if (v1.value().nonEmpty) {
      v1
    } else {
      throw EmptyValueException("Record is empty")
    }
  }

}

class EventLogParser
  extends Executor[ConsumerRecord[String, String], EventLog]
  with LazyLogging {

  override def apply(v1: ConsumerRecord[String, String]): EventLog = {
    val result: EventLog = try {
      FromString[EventLog](v1.value()).get
    } catch {
      case e: Exception =>
        logger.error("Error Parsing Event: " + e.getMessage)
        throw ParsingIntoEventLogException("Error Parsing Into Event Log", v1.value())
    }

    result
  }

}

class EventsStore @Inject() (events: Events)(implicit ec: ExecutionContext)
  extends Executor[EventLog, Future[Unit]]
  with LazyLogging {

  override def apply(v1: EventLog): Future[Unit] = {
    events.insert(v1).recover {
      case e: Exception =>
        logger.error("Error storing data: " + e.getMessage)
        throw StoringIntoEventLogException("Error storing data", v1, e.getMessage)
    }
  }

}

trait ExecutorFamily {

  def filterEmpty: FilterEmpty

  def eventsStore: EventsStore

  def eventLogParser: EventLogParser

}

@Singleton
case class DefaultExecutorFamily @Inject() (
    filterEmpty: FilterEmpty,
    eventLogParser: EventLogParser,
    eventsStore: EventsStore
) extends ExecutorFamily

@Singleton
class DefaultExecutor @Inject() (val reporter: Reporter, executorFamily: ExecutorFamily) {

  import UUIDHelper._
  import executorFamily._

  def composed = filterEmpty andThen eventLogParser andThen eventsStore

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
