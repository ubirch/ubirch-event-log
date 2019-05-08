package com.ubirch.adapter.process

import java.io.ByteArrayInputStream

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ProducerConfPaths
import com.ubirch.adapter.models.Encodings
import com.ubirch.adapter.services.kafka.consumer.MessageEnvelopePipeData
import com.ubirch.adapter.util.AdapterJsonSupport
import com.ubirch.adapter.util.Exceptions._
import com.ubirch.kafka.producer.StringProducer
import com.ubirch.models.EnrichedEventLog.enrichedEventLog
import com.ubirch.models.EventLog
import com.ubirch.process.Executor
import com.ubirch.util.Implicits.enrichedConfig
import com.ubirch.util._
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.{ ProducerRecord, RecordMetadata }

import scala.concurrent.{ ExecutionContext, Future }

class JValueFromConsumerRecord @Inject() (implicit ec: ExecutionContext)
  extends Executor[Vector[ConsumerRecord[String, Array[Byte]]], Future[MessageEnvelopePipeData]]
  with LazyLogging {

  import org.json4s.jackson.JsonMethods._

  override def apply(v1: Vector[ConsumerRecord[String, Array[Byte]]]): Future[MessageEnvelopePipeData] = Future {
    val result: MessageEnvelopePipeData = try {

      val maybeJValue = v1.map { x => new ByteArrayInputStream(x.value()) }.map { x => parse(x) }.headOption

      MessageEnvelopePipeData(v1, maybeJValue, None, None, None)

    } catch {
      case e: Exception =>
        logger.error("Error Parsing Into Bytes into JValue: {}", e.getMessage)
        throw JValueFromConsumerRecordException("Error Parsing Into Event Log", MessageEnvelopePipeData(v1, None, None, None, None))
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
class EventLogFromConsumerRecord @Inject() (implicit ec: ExecutionContext)
  extends Executor[Future[MessageEnvelopePipeData], Future[MessageEnvelopePipeData]]
  with LazyLogging {

  def decode(messageEnvelopePipeData: MessageEnvelopePipeData) = Encodings.UPA(messageEnvelopePipeData)

  override def apply(v1: Future[MessageEnvelopePipeData]): Future[MessageEnvelopePipeData] = v1.map { v1 =>
    val result: MessageEnvelopePipeData = try {

      val jValue = v1.messageJValue.getOrElse { throw EventLogFromConsumerRecordException("No JValue Found", v1) }
      val decoded = decode(v1)(jValue)

      decoded

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
  extends Executor[Future[MessageEnvelopePipeData], Future[MessageEnvelopePipeData]]
  with LazyLogging {

  override def apply(v1: Future[MessageEnvelopePipeData]): Future[MessageEnvelopePipeData] = v1.map { v1 =>

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
  extends Executor[Future[MessageEnvelopePipeData], Future[MessageEnvelopePipeData]]
  with LazyLogging
  with ProducerConfPaths {
  override def apply(v1: Future[MessageEnvelopePipeData]): Future[MessageEnvelopePipeData] = {

    v1.map { v1 =>

      try {

        val topic = config.getStringAsOption(TOPIC_PATH).getOrElse("com.ubirch.eventlog")

        val output = v1.eventLog
          .map(AdapterJsonSupport.ToJson[EventLog])
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
  * @param stringProducer Represents a producer.
  * @param config         Represents a config object to read config values from
  * @param ec             Represents an execution context
  */
class Commit @Inject() (stringProducer: StringProducer, config: Config)(implicit ec: ExecutionContext) extends Executor[Future[MessageEnvelopePipeData], Future[MessageEnvelopePipeData]] {

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

  override def apply(v1: Future[MessageEnvelopePipeData]): Future[MessageEnvelopePipeData] = {

    v1.flatMap { v1 =>

      try {

        v1.producerRecord
          .map(x => commit(x))
          .map(x => x.map(y => v1.copy(recordMetadata = y)))
          .getOrElse(throw CommitException("No Producer Record Found", v1))

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
