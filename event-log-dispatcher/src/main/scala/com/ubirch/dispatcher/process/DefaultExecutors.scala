package com.ubirch.dispatcher.process

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ProducerConfPaths
import com.ubirch.dispatcher.services.DispatchInfo
import com.ubirch.dispatcher.services.kafka.consumer.DispatcherPipeData
import com.ubirch.dispatcher.services.metrics.DefaultDispatchingCounter
import com.ubirch.dispatcher.util.Exceptions._
import com.ubirch.models.EnrichedEventLog.enrichedEventLog
import com.ubirch.models.{ EventLog, Values }
import com.ubirch.process.{ BasicCommit, Executor, MetricsLoggerBasic }
import com.ubirch.services.metrics.Counter
import com.ubirch.util._
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class FilterEmpty @Inject() (implicit ec: ExecutionContext)
  extends Executor[Vector[ConsumerRecord[String, String]], Future[DispatcherPipeData]]
  with LazyLogging {

  override def apply(v1: Vector[ConsumerRecord[String, String]]): Future[DispatcherPipeData] = Future {

    val pd = DispatcherPipeData(v1, None, Vector.empty, Vector.empty)
    if (v1.headOption.exists(_.value().nonEmpty)) {
      pd
    } else {
      //logger.error("Record is empty")
      throw EmptyValueException("Record is empty", pd)
    }
  }

  def apply(v1: ConsumerRecord[String, String]): Future[DispatcherPipeData] = apply(Vector(v1))

}

/**
  * Executor that transforms a ConsumerRecord into an EventLog
  * @param ec Represent the execution context for asynchronous processing.
  */
@Singleton
class EventLogParser @Inject() (implicit ec: ExecutionContext)
  extends Executor[Future[DispatcherPipeData], Future[DispatcherPipeData]]
  with LazyLogging {

  override def apply(v1: Future[DispatcherPipeData]): Future[DispatcherPipeData] = v1.map { v1 =>
    val result: DispatcherPipeData = try {
      val eventLog = v1.consumerRecords.map { x =>
        logger.debug("EventLogParser:" + x.value())
        EventLogJsonSupport.FromString[EventLog](x.value()).get
      }.headOption
      v1.copy(eventLog = eventLog.map(_.addTraceHeader(Values.DISPATCHER_SYSTEM)))
    } catch {
      case e: Exception =>
        logger.error("Error Parsing Event: " + e.getMessage)
        throw ParsingIntoEventLogException("Error Parsing Into Event Log", v1)
    }

    result
  }

}

/**
  * Represents an executor that creates the producer record object that will be eventually published to Kafka
  *
  * @param config Represents a config object to read config values from
  * @param ec     Represents an execution context
  */
@Singleton
class CreateProducerRecords @Inject() (
    config: Config,
    dispatchInfo: DispatchInfo,
    @Named(DefaultDispatchingCounter.name) counter: Counter
)(implicit ec: ExecutionContext)
  extends Executor[Future[DispatcherPipeData], Future[DispatcherPipeData]]
  with ProducerConfPaths
  with LazyLogging {
  override def apply(v1: Future[DispatcherPipeData]): Future[DispatcherPipeData] = {

    import EventLogJsonSupport._

    v1.map { v1 =>

      try {

        val output = v1.eventLog
          .flatMap(x => dispatchInfo.info.find(_.category == x.category).map(y => (x, y)))
          .map { case (x, y) =>
            val commitDecision: Vector[Decision[ProducerRecord[String, String]]] = {

              import org.json4s._

              logger.debug(s"Creating PR for EventLog(${x.category}, ${x.id})")

              val eventLogJson = EventLogJsonSupport.ToJson[EventLog](x).get

              y.topics.map { t =>

                val dataToSend: String = t.dataToSend.filter(_.nonEmpty).flatMap { dts =>
                  val dataFromEventLog = eventLogJson \ dts
                  dataFromEventLog.extractOpt[String]
                }.orElse {
                  val data = Option(x.toJson)
                  counter.counter.labels(t.name).inc()
                  data
                }.getOrElse(throw CreateProducerRecordException("Empty Materials 2: No data field extracted.", v1))

                logger.debug(s"Dispatching to $t: " + dataToSend)

                Go(ProducerRecordHelper.toRecord(t.name, v1.id.toString, dataToSend, Map.empty))

              }.toVector

            }

            commitDecision
          }
          .map(x => v1.copy(producerRecords = x))
          .getOrElse(throw CreateProducerRecordException("Empty Materials 1", v1))

        output

      } catch {

        case e: Exception =>
          throw CreateProducerRecordException(s"dispatchInfo: ${dispatchInfo.info} / current error: ${e.getMessage}", v1)

      }
    }

  }
}

@Singleton
class Commit @Inject() (basicCommitter: BasicCommit, metricsLoggerBasic: MetricsLoggerBasic)(implicit ec: ExecutionContext)
  extends Executor[Future[DispatcherPipeData], Future[DispatcherPipeData]]
  with LazyLogging {

  override def apply(v1: Future[DispatcherPipeData]): Future[DispatcherPipeData] = {

    val futureMetadata = v1.map(_.producerRecords)
      .flatMap { prs =>
        Future.sequence {
          prs.map { x =>
            val futureResp = basicCommitter(x)
            futureResp.map {
              case Some(_) => metricsLoggerBasic.incSuccess
              case None => metricsLoggerBasic.incFailure
            }
            futureResp
          }
        }
      }.map { x =>
        x.flatMap(_.toVector)
      }

    val futureResp = for {
      md <- futureMetadata
      v <- v1
    } yield {
      v.copy(recordsMetadata = md)
    }

    futureResp.recoverWith {
      case e: Exception =>
        logger.error("Error committing: {} ", e.getMessage)
        v1.flatMap { x =>
          Future.failed {
            CommitException(e.getMessage, x)
          }
        }
    }

  }
}

/**
  * Represents a description of a family of executors that can be composed.
  */
trait ExecutorFamily {

  def filterEmpty: FilterEmpty
  def eventLogParser: EventLogParser
  def createProducerRecords: CreateProducerRecords
  def commit: Commit

}

@Singleton
class DefaultExecutorFamily @Inject() (
    val filterEmpty: FilterEmpty,
    val eventLogParser: EventLogParser,
    val createProducerRecords: CreateProducerRecords,
    val commit: Commit
) extends ExecutorFamily
