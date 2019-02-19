package com.ubirch.process

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.{ Error, EventLog, Events }
import com.ubirch.services.kafka.consumer.PipeData
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.util.Exceptions._
import com.ubirch.util.{ FromString, UUIDHelper }
import io.prometheus.client.Counter
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

/**
  * Represents a process to be executed.
  * It allows for Executor composition with the operator andThen
  * @tparam T1 the input to the pipe
  * @tparam R the output of the pipe
  */
trait Executor[-T1, +R] extends (T1 => R) {
  self =>

  override def apply(v1: T1): R

  def andThen[Q](other: Executor[R, Q]): Executor[T1, Q] = {
    v1: T1 => other(self(v1))
  }

}

/**
  * Executor that filters ConsumerRecords values.
  * @param ec Represent the execution context for asynchronous processing.
  */

class FilterEmpty @Inject() (implicit ec: ExecutionContext)
  extends Executor[ConsumerRecord[String, String], Future[PipeData]]
  with LazyLogging {

  override def apply(v1: ConsumerRecord[String, String]): Future[PipeData] = Future {
    val pd = PipeData(v1, None)
    if (v1.value().nonEmpty) {
      pd
    } else {
      logger.error("Record is empty")
      throw EmptyValueException("Record is empty", pd)
    }
  }

}

/**
  * Executor that transforms a ConsumerRecord into an EventLog
  * @param ec Represent the execution context for asynchronous processing.
  */
class EventLogParser @Inject() (implicit ec: ExecutionContext)
  extends Executor[Future[PipeData], Future[PipeData]]
  with LazyLogging {

  override def apply(v1: Future[PipeData]): Future[PipeData] = v1.map { v1 =>
    val result: PipeData = try {
      val eventLog = FromString[EventLog](v1.consumerRecord.value()).get
      v1.copy(eventLog = Some(eventLog))
    } catch {
      case e: Exception =>
        logger.error("Error Parsing Event: " + e.getMessage)
        throw ParsingIntoEventLogException("Error Parsing Into Event Log", v1)
    }

    result
  }

}

/**
  * Executor that stores an EventLog into Cassandra by Using the Events value.
  * @param events Represents the DAO for the Events type.
  * @param ec Represent the execution context for asynchronous processing.
  */
class EventsStore @Inject() (events: Events)(implicit ec: ExecutionContext)
  extends Executor[Future[PipeData], Future[PipeData]]
  with LazyLogging {

  override def apply(v1: Future[PipeData]): Future[PipeData] = v1.flatMap { v1 =>
    v1.eventLog.map { el =>

      events.insert(el).map(_ => v1).recover {
        case e: Exception =>
          logger.error("Error storing data: " + e.getMessage)
          throw StoringIntoEventLogException("Error storing data", v1, e.getMessage)
      }

    }.getOrElse {
      logger.error("Error storing data: EventLog Data Not Defined")
      Future.successful(throw StoringIntoEventLogException("Error storing data", v1, "EventLog Data Not Defined"))
    }

  }

}

class MetricsLogger @Inject() (implicit ec: ExecutionContext) extends Executor[Future[PipeData], Future[PipeData]] {

  final val counter = Counter.build()
    .name("events_total").help("Total events.")
    .labelNames("result")
    .register()

  override def apply(v1: Future[PipeData]): Future[PipeData] = {
    v1.onComplete {
      case Success(_) =>
        counter.labels("success").inc()
      case Failure(_) =>
        counter.labels("failure").inc()
    }

    v1
  }
}

/**
  * A convenience type to aggregate executors for later injection
  */

trait ExecutorFamily {

  def filterEmpty: FilterEmpty

  def eventsStore: EventsStore

  def eventLogParser: EventLogParser

  def metricsLogger: MetricsLogger

}

/**
  * Default materialization of the family of executors
  * @param filterEmpty Executor that filters ConsumerRecords
  * @param eventLogParser Executor that parses a ConsumerRecord into an Event Log
  * @param eventsStore Executor that stores an EventLog into Cassandra
  */
@Singleton
case class DefaultExecutorFamily @Inject() (
    filterEmpty: FilterEmpty,
    eventLogParser: EventLogParser,
    eventsStore: EventsStore,
    metricsLogger: MetricsLogger
) extends ExecutorFamily

/**
  * Default Executor Composer Convenience for creating executor compositions and
  * executor exceptions management.
  * @param reporter Represents a convenience type that allows to report to a producer.
  * @param executorFamily Represents a family of executors.
  */
@Singleton
class DefaultExecutor @Inject() (val reporter: Reporter, executorFamily: ExecutorFamily)(implicit ec: ExecutionContext) {

  import UUIDHelper._
  import executorFamily._
  import reporter.Types._

  private def uuid = timeBasedUUID

  final val counter: Counter = Counter.build()
    .name("event_error_total")
    .help("Total event errors.")
    .labelNames("result")
    .register()

  def composed: Executor[ConsumerRecord[String, String], Future[PipeData]] =
    filterEmpty andThen eventLogParser andThen eventsStore andThen metricsLogger

  def executor: Executor[ConsumerRecord[String, String], Future[PipeData]] = composed

  //TODO We are not waiting for the error to be sent. We need to see if that maybe a problem
  def executorExceptionHandler: PartialFunction[Throwable, Future[PipeData]] = {
    case e: EmptyValueException =>
      counter.labels("EmptyValueException").inc()
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name))
      Future.successful(e.pipeData)
    case e: ParsingIntoEventLogException =>
      counter.labels("ParsingIntoEventLogException").inc()
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = e.pipeData.consumerRecord.value()))
      Future.successful(e.pipeData)
    case e: StoringIntoEventLogException =>
      counter.labels("StoringIntoEventLogException").inc()
      reporter.report(
        Error(
          id = e.pipeData.eventLog.map(_.id).getOrElse(uuid),
          message = e.getMessage,
          exceptionName = e.name,
          value = e.pipeData.eventLog.toString
        )
      )

      val res = e.pipeData.eventLog.map { el =>
        Future.failed(NeedForPauseException("Requesting Pause", el, e.getMessage))
      }.getOrElse {
        Future.successful(e.pipeData)
      }

      res

  }

}
