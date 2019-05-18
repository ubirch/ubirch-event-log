package com.ubirch.dispatcher.process

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ProducerConfPaths
import com.ubirch.dispatcher.services.DispatchInfo
import com.ubirch.dispatcher.services.kafka.consumer.DispatcherPipeData
import com.ubirch.dispatcher.util.Exceptions._
import com.ubirch.kafka.producer.StringProducer
import com.ubirch.models.EventLog
import com.ubirch.process.Executor
import com.ubirch.util._
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.{ ProducerRecord, RecordMetadata }

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
      val eventLog = v1.consumerRecords.map(x => EventLogJsonSupport.FromString[EventLog](x.value()).get).headOption
      v1.copy(eventLog = eventLog)
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
class CreateProducerRecords @Inject() (config: Config, dispatchInfo: DispatchInfo)(implicit ec: ExecutionContext)
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
                  Option(x.toJson)
                }.getOrElse(throw CreateProducerRecordException("Empty Materials 2: No data field extracted.", v1))

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
class Commit @Inject() (basicCommitter: BasicCommit)(implicit ec: ExecutionContext)
  extends Executor[Future[DispatcherPipeData], Future[DispatcherPipeData]]
  with LazyLogging {

  override def apply(v1: Future[DispatcherPipeData]): Future[DispatcherPipeData] = {

    val futureMetadata = v1.map(_.producerRecords)
      .flatMap { prs =>
        Future.sequence {
          prs.map(x => basicCommitter(x))
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

class BasicCommit @Inject() (stringProducer: StringProducer, config: Config)(implicit ec: ExecutionContext)
  extends Executor[Decision[ProducerRecord[String, String]], Future[Option[RecordMetadata]]] {

  val futureHelper = new FutureHelper()

  def commit(value: Decision[ProducerRecord[String, String]]): Future[Option[RecordMetadata]] = {
    value match {
      case Go(record) =>
        val javaFuture = stringProducer.getProducerOrCreate.send(record)
        futureHelper.fromJavaFuture(javaFuture).map(x => Option(x))
      case Ignore() =>
        Future.successful(None)
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
