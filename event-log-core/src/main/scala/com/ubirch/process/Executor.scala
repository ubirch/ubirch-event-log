package com.ubirch.process

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.{ Error, EventLog, Events }
import com.ubirch.services.kafka.consumer.PipeData
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.util.Exceptions._
import com.ubirch.util.{ FromString, UUIDHelper }
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.concurrent.{ ExecutionContext, Future }

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
  */

class FilterEmpty extends Executor[ConsumerRecord[String, String], PipeData] {

  override def apply(v1: ConsumerRecord[String, String]): PipeData = {
    val pd = PipeData(v1, None)
    if (v1.value().nonEmpty) {
      pd
    } else {
      throw EmptyValueException("Record is empty", pd)
    }
  }

}

/**
  * Executor that transforms a ConsumerRecord into an EventLog
  */
class EventLogParser
  extends Executor[PipeData, PipeData]
  with LazyLogging {

  override def apply(v1: PipeData): PipeData = {
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
  extends Executor[PipeData, Future[PipeData]]
  with LazyLogging {

  override def apply(v1: PipeData): Future[PipeData] = {
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

/**
  * A convenience type to aggregate executors for later injection
  */

trait ExecutorFamily {

  def filterEmpty: FilterEmpty

  def eventsStore: EventsStore

  def eventLogParser: EventLogParser

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
    eventsStore: EventsStore
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

  def composed: Executor[ConsumerRecord[String, String], Future[PipeData]] = filterEmpty andThen eventLogParser andThen eventsStore

  def executor: Executor[ConsumerRecord[String, String], Future[PipeData]] = composed

  private def uuid = timeBasedUUID

  import reporter.Types._

  //TODO We are not waiting for the error to be sent. We need to see if that maybe a problem
  val executorExceptionHandler: PartialFunction[Throwable, Future[PipeData]] = {
    case e: EmptyValueException =>
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name))
      Future.successful(e.pipeData)
    case e: ParsingIntoEventLogException =>
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = e.pipeData.consumerRecord.value()))
      Future.successful(e.pipeData)
    case e: StoringIntoEventLogException =>

      reporter.report(
        Error(
          id = e.pipeData.eventLog.map(_.id).getOrElse(uuid),
          message = e.getMessage,
          exceptionName = e.name,
          value = e.pipeData.eventLog.toString
        )
      )

      val t = e.pipeData.eventLog.map { el =>
        Future.failed(NeedForPauseException("Requesting Pause", el, e.getMessage))
      }.getOrElse {
        Future.successful(e.pipeData)
      }

      t

  }

}
