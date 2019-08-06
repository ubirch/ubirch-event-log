package com.ubirch.encoder.process

import java.io.ByteArrayInputStream

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ProducerConfPaths
import com.ubirch.encoder.models.Encodings
import com.ubirch.encoder.services.kafka.consumer.EncoderPipeData
import com.ubirch.encoder.util.EncoderJsonSupport
import com.ubirch.encoder.util.Exceptions._
import com.ubirch.models.EnrichedEventLog.enrichedEventLog
import com.ubirch.models.{ EventLog, Values }
import com.ubirch.process.{ BasicCommit, BasicCommitUnit, Executor, MetricsLoggerBasic }
import com.ubirch.util.Implicits.enrichedConfig
import com.ubirch.util._
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord

import scala.concurrent.{ ExecutionContext, Future }

class JValueFromConsumerRecord @Inject() (implicit ec: ExecutionContext)
  extends Executor[Vector[ConsumerRecord[String, Array[Byte]]], Future[EncoderPipeData]]
  with LazyLogging {

  import org.json4s.jackson.JsonMethods._

  override def apply(v1: Vector[ConsumerRecord[String, Array[Byte]]]): Future[EncoderPipeData] = Future {
    val result: EncoderPipeData = try {

      val maybeJValue = v1.map { x => new ByteArrayInputStream(x.value()) }.map { x => parse(x) }.headOption

      EncoderPipeData(v1, maybeJValue, None, None, None)

    } catch {
      case e: Exception =>
        logger.error("Error Parsing Into Bytes into JValue: {}", e.getMessage)
        throw JValueFromConsumerRecordException("Error Parsing Into Event Log", EncoderPipeData(v1, None, None, None, None))
    }

    result
  }

}

/**
  * Represents an executor that converts a consumer record of type String, MessageEnvelope into
  * an EventLog and wraps these values into the pipeline data.
  *
  * @param ec Represents an execution context
  */
class EventLogFromConsumerRecord @Inject() (encodings: Encodings)(implicit ec: ExecutionContext)
  extends Executor[Future[EncoderPipeData], Future[EncoderPipeData]]
  with LazyLogging {

  def decode(encoderPipeData: EncoderPipeData) = {
    encodings.UPP(encoderPipeData).orElse(encodings.PublichBlockchain(encoderPipeData))
  }

  override def apply(v1: Future[EncoderPipeData]): Future[EncoderPipeData] = v1.map { v1 =>
    val result: EncoderPipeData = try {

      val jValue = v1.messageJValue.getOrElse { throw EventLogFromConsumerRecordException("No JValue Found", v1) }
      val decoded = decode(v1)(jValue)

      val withTrace = decoded.copy(eventLog = decoded.eventLog.map(_.addBlueMark.addTraceHeader(Values.ENCODER_SYSTEM)))
      logger.debug("EventLogFromConsumerRecord:" + withTrace.eventLog.map(_.toJson).getOrElse("No Data decoded"))

      withTrace

    } catch {
      case e: EventLogFromConsumerRecordException =>
        throw e
      case e: Exception =>
        logger.error("Error Parsing Into Event Log: {}", e.getMessage)
        throw EventLogFromConsumerRecordException("Error Parsing Into Event Log", v1)
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
  extends Executor[Future[EncoderPipeData], Future[EncoderPipeData]]
  with LazyLogging {

  override def apply(v1: Future[EncoderPipeData]): Future[EncoderPipeData] = v1.map { v1 =>

    v1.eventLog.map { el =>
      try {
        val signedEventLog = el.sign(config)
        v1.copy(eventLog = Some(signedEventLog))
      } catch {
        case e: Exception =>
          logger.error("Error signing data: {}", e.getMessage)
          throw SigningEventLogException("Error signing data", v1)
      }

    }.getOrElse {
      logger.error("No EventLog Found")
      throw SigningEventLogException("No EventLog Found", v1)
    }
  }
}

/**
  * Represents an executor that creates the producer record object that will be eventually published to Kafka
  *
  * @param config Represents a config object to read config values from
  * @param ec     Represents an execution context
  */
class CreateProducerRecord @Inject() (config: Config)(implicit ec: ExecutionContext)
  extends Executor[Future[EncoderPipeData], Future[EncoderPipeData]]
  with LazyLogging
  with ProducerConfPaths {
  override def apply(v1: Future[EncoderPipeData]): Future[EncoderPipeData] = {

    v1.map { v1 =>

      try {

        val topic = config.getStringAsOption(TOPIC_PATH).getOrElse("com.ubirch.eventlog")

        val output = v1.eventLog
          .map(EncoderJsonSupport.ToJson[EventLog])
          .map { x =>
            val commitDecision: Decision[ProducerRecord[String, String]] = {
              v1.producerRecord.getOrElse {
                Go(ProducerRecordHelper.toRecord(topic, v1.id.toString, x.toString, Map.empty))
              }
            }

            commitDecision

          }
          .map(x => v1.copy(producerRecord = Some(x)))
          .getOrElse(throw CreateProducerRecordException("Empty Materials: Either the eventlog or/and the producer record are empty.", v1))

        output

      } catch {

        case e: Exception =>
          logger.error("Error Creating producer record: {}", e.getMessage)
          throw CreateProducerRecordException(e.getMessage, v1)

      }
    }

  }
}

/**
  * Represents an executor that commits a producer record
  *
  * @param basicCommit Simple entity for committing to kafka
  * @param ec             Represents an execution context
  */
class Commit @Inject() (basicCommit: BasicCommitUnit, metricsLoggerBasic: MetricsLoggerBasic)(implicit ec: ExecutionContext)
  extends Executor[Future[EncoderPipeData], Future[EncoderPipeData]]
  with LazyLogging {

  override def apply(v1: Future[EncoderPipeData]): Future[EncoderPipeData] = {

    v1.flatMap { v1 =>

      try {

        val resp = v1.producerRecord
          .flatMap(x => basicCommit(x))

        Future.successful(v1.copy(recordMetadata = resp))

      } catch {

        case e: Exception =>
          Future.failed(CommitException(e.getMessage, v1))

      }
    }

  }
}

/**
  * Represents a description of a family of executors that can be composed.
  */
trait ExecutorFamily {

  def jValueFromConsumerRecord: JValueFromConsumerRecord

  def eventLogFromConsumerRecord: EventLogFromConsumerRecord

  def createProducerRecord: CreateProducerRecord

  def eventLogSigner: EventLogSigner

  def commit: Commit

}

/**
  * Represents a family of executors
  *
  * @param eventLogFromConsumerRecord Represents an executor that converts a consumer record of type String, MessageEnvelope into
  *                                   an EventLog and wraps these values into the pipeline data.
  * @param createProducerRecord       Represents an executor that creates the producer record object that will be eventually published to Kafka
  * @param commit                     Represents an executor that commits a producer record
  */
@Singleton
class DefaultExecutorFamily @Inject() (
    val jValueFromConsumerRecord: JValueFromConsumerRecord,
    val eventLogFromConsumerRecord: EventLogFromConsumerRecord,
    val createProducerRecord: CreateProducerRecord,
    val eventLogSigner: EventLogSigner,
    val commit: Commit
) extends ExecutorFamily
