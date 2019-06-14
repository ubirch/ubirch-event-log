package com.ubirch.process

import com.datastax.driver.core.exceptions.InvalidQueryException
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ProducerConfPaths
import com.ubirch.kafka.producer.StringProducer
import com.ubirch.models.EnrichedEventLog.enrichedEventLog
import com.ubirch.models.{ EventLog, EventsDAO }
import com.ubirch.services.kafka.consumer.PipeData
import com.ubirch.services.metrics.Counter
import com.ubirch.util.Exceptions._
import com.ubirch.util._
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.{ ProducerRecord, RecordMetadata }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

/**
  * Represents a process to be executed.
  * It allows for Executor composition with the operator andThen
  * @tparam T1 the input to the pipe
  * @tparam R  the output of the pipe
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
  extends Executor[Vector[ConsumerRecord[String, String]], Future[PipeData]]
  with LazyLogging {

  override def apply(v1: Vector[ConsumerRecord[String, String]]): Future[PipeData] = Future {

    val pd = PipeData(v1, None)
    if (v1.headOption.exists(_.value().nonEmpty)) {
      pd
    } else {
      //logger.error("Record is empty")
      throw EmptyValueException("Record is empty", pd)
    }
  }

  def apply(v1: ConsumerRecord[String, String]): Future[PipeData] = apply(Vector(v1))

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
      val eventLog = v1.consumerRecords.map(x => EventLogJsonSupport.FromString[EventLog](x.value()).get).headOption
      v1.copy(eventLog = eventLog)
    } catch {
      case _: Exception =>
        //logger.error("Error Parsing Event: " + e.getMessage)
        throw ParsingIntoEventLogException("Error Parsing Into Event Log", v1)
    }

    result
  }

}

/**
  *  Executor that signs an EventLog
  *
  * @param config Represents a config object
  * @param ec Represent the execution context for asynchronous processing.
  */
class EventLogSigner @Inject() (config: Config)(implicit ec: ExecutionContext)
  extends Executor[Future[PipeData], Future[PipeData]]
  with LazyLogging {

  override def apply(v1: Future[PipeData]): Future[PipeData] = v1.map { v1 =>
    v1.eventLog.map { el =>
      try {
        val signedEventLog = el.sign(config)
        v1.copy(eventLog = Some(signedEventLog))
      } catch {
        case _: Exception =>
          throw SigningEventLogException("Error signing data", v1)
      }

    }.getOrElse {
      throw SigningEventLogException("No EventLog Found", v1)
    }
  }
}

/**
  * Executor that stores an EventLog into Cassandra by Using the Events value.
  * @param events Represents the DAO for the Events type.
  * @param ec     Represent the execution context for asynchronous processing.
  */
class EventsStore @Inject() (events: EventsDAO)(implicit ec: ExecutionContext)
  extends Executor[Future[PipeData], Future[PipeData]]
  with LazyLogging {

  override def apply(v1: Future[PipeData]): Future[PipeData] = v1.flatMap { v1 =>
    v1.eventLog.map { el =>
      events.insertFromEventLog(el).map { x =>
        val expectedNumber = 1 + el.lookupKeys.flatMap(_.value).size
        logger.debug(s"EventLog(${el.category}, ${el.id}) with $expectedNumber items, $x were stored")
        v1
      }.recover {
        case e: InvalidQueryException =>
          logger.error("Error storing data: " + e)
          throw e
        case e: Exception =>
          logger.error("Error storing data: " + e)
          throw StoringIntoEventLogException("Error storing data", v1, e.getMessage)
      }

    }.getOrElse {
      //logger.error("Error storing data: EventLog Data Not Defined")
      Future.successful(throw StoringIntoEventLogException("Error storing data", v1, "EventLog Data Not Defined"))
    }

  }

}

@Singleton
class DiscoveryExecutor @Inject() (basicCommit: BasicCommit, config: Config)(implicit ec: ExecutionContext)
  extends Executor[Future[PipeData], Future[PipeData]]
  with ProducerConfPaths
  with LazyLogging {

  val topic: String = config.getString(TOPIC_PATH)

  logger.debug("Discovery Topic: " + topic)

  override def apply(v1: Future[PipeData]): Future[PipeData] = {
    v1.flatMap { v1 =>

      v1.discoveryData match {
        case Nil =>
          Future.successful(v1)
        case _ =>

          val relationsAsString = EventLogJsonSupport.ToJson(v1.discoveryData).toString

          val pr = ProducerRecordHelper.toRecord(topic, null, relationsAsString, Map.empty)

          basicCommit(Go(pr)).map {
            case Some(_) => v1
            case None => throw DiscoveryException("Error sending discovery data", v1, "Error Committing Discovery Data")
          }
      }

    }

  }

}

class MetricsLogger @Inject() (@Named("DefaultMetricsLoggerCounter") counter: Counter)(implicit ec: ExecutionContext)
  extends Executor[Future[PipeData], Future[PipeData]] {

  override def apply(v1: Future[PipeData]): Future[PipeData] = {
    v1.onComplete {
      case Success(_) =>
        counter.counter.labels("success").inc()
      case Failure(_) =>
        counter.counter.labels("failure").inc()
    }

    v1
  }
}

@Singleton
class BasicCommit @Inject() (stringProducer: StringProducer)(implicit ec: ExecutionContext)
  extends Executor[Decision[ProducerRecord[String, String]], Future[Option[RecordMetadata]]] {

  val futureHelper = new FutureHelper()

  def send(pr: ProducerRecord[String, String]) = {
    val javaFuture = stringProducer.getProducerOrCreate.send(pr)
    futureHelper.fromJavaFuture(javaFuture).map(x => Option(x))
  }

  def commit(value: Decision[ProducerRecord[String, String]]): Future[Option[RecordMetadata]] = {
    value match {
      case Go(record) => send(record)
      case Ignore() => Future.successful(None)
    }
  }

  override def apply(v1: Decision[ProducerRecord[String, String]]): Future[Option[RecordMetadata]] = {

    try {
      commit(v1)
    } catch {
      case e: Exception =>
        Future.failed(BasicCommitException(e.getMessage))
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

  def eventLogSigner: EventLogSigner

  def discoveryExecutor: DiscoveryExecutor

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
    eventLogSigner: EventLogSigner,
    eventsStore: EventsStore,
    discoveryExecutor: DiscoveryExecutor,
    metricsLogger: MetricsLogger
) extends ExecutorFamily
