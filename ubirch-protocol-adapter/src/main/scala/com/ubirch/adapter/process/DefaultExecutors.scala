package com.ubirch.adapter.process

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ProducerConfPaths
import com.ubirch.adapter.ServiceTraits
import com.ubirch.adapter.services.kafka.consumer.MessageEnvelopePipeData
import com.ubirch.adapter.util.AdapterJsonSupport
import com.ubirch.adapter.util.Exceptions._
import com.ubirch.kafka.MessageEnvelope
import com.ubirch.kafka.producer.StringProducer
import com.ubirch.models.EnrichedEventLog.enrichedEventLog
import com.ubirch.models.EventLog
import com.ubirch.process.Executor
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.util.Implicits.enrichedConfig
import com.ubirch.util._
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.{ ProducerRecord, RecordMetadata }

import scala.concurrent.{ ExecutionContext, Future }

/**
  * Represents an executor that converts a consumer record of type String, MessageEnvelope into
  * an EventLog and wraps these values into the pipeline data.
  *
  * @param ec Represents an execution context
  */
class EventLogFromConsumerRecord @Inject() (implicit ec: ExecutionContext)
  extends Executor[Vector[ConsumerRecord[String, MessageEnvelope]], Future[MessageEnvelopePipeData]]
  with LazyLogging {

  import AdapterJsonSupport._
  import org.json4s.jackson.JsonMethods._

  override def apply(v1: Vector[ConsumerRecord[String, MessageEnvelope]]): Future[MessageEnvelopePipeData] = Future {
    val result: MessageEnvelopePipeData = try {

      val maybeEventLog = v1.headOption.map(_.value()).map { messageEnvelope =>
        val jValueCustomerId = messageEnvelope.context \\ EventLogFromConsumerRecord.CUSTOMER_ID_FIELD
        val customerId = jValueCustomerId.extractOpt[String]
          .filter(_.nonEmpty)
          .getOrElse {
            throw EventLogFromConsumerRecordException(
              "No CustomerId found",
              MessageEnvelopePipeData(v1, None, None, None)
            )
          }

        val payloadToJson = AdapterJsonSupport.ToJson[ProtocolMessage](messageEnvelope.ubirchPacket)

        val payload = payloadToJson.get

        if (messageEnvelope.ubirchPacket.getHint == 0) {

          val payloadHash = fromJsonNode(messageEnvelope.ubirchPacket.getPayload)
            .extractOpt[String]
            .filter(_.nonEmpty)
            .getOrElse {
              throw EventLogFromConsumerRecordException(
                "Payload not found or is empty",
                MessageEnvelopePipeData(v1, None, None, None)
              )
            }

          EventLog("EventLogFromConsumerRecord", ServiceTraits.ADAPTER_CATEGORY, payload)
            .withCustomerId(customerId)
            .withNewId(payloadHash)

        } else {
          EventLog("EventLogFromConsumerRecord", ServiceTraits.ADAPTER_CATEGORY, payload)
            .withCustomerId(customerId)
        }

      }

      MessageEnvelopePipeData(v1, maybeEventLog, None, None)

    } catch {
      case e: EventLogFromConsumerRecordException =>
        throw e
      case e: Exception =>
        logger.error("Error Parsing Into Event Log: {}", e.getMessage)
        throw EventLogFromConsumerRecordException("Error Parsing Into Event Log", MessageEnvelopePipeData(v1, None, None, None))
    }

    result
  }

}

/**
  * Companion object for the executor EventLogFromConsumerRecord
  */
object EventLogFromConsumerRecord {
  val CUSTOMER_ID_FIELD = "customerId"

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
        case _: Exception =>
          throw SigningEventLogException("Error signing data", v1)
      }

    }.getOrElse {
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
  with ProducerConfPaths {
  override def apply(v1: Future[MessageEnvelopePipeData]): Future[MessageEnvelopePipeData] = {

    v1.map { v1 =>

      try {

        val topic = config.getStringAsOption(TOPIC_PATH).getOrElse("com.ubirch.eventlog")

        val output = v1.eventLog
          .map(AdapterJsonSupport.ToJson[EventLog])
          .map { x =>
            val commitDecision: Decision[ProducerRecord[String, String]] = {
              if (v1.consumerRecords.headOption.exists(_.value().ubirchPacket.getHint == 0)) {
                Go(ProducerRecordHelper.toRecord(topic, v1.id.toString, x.toString, Map.empty))
              } else {
                Ignore()
              }
            }

            commitDecision

          }
          .map(x => v1.copy(producerRecord = Some(x)))
          .getOrElse(throw CreateProducerRecordException("Empty Materials: Either the eventlog or/and the producer record are empty.", v1))

        output

      } catch {

        case e: Exception =>
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
    val eventLogFromConsumerRecord: EventLogFromConsumerRecord,
    val createProducerRecord: CreateProducerRecord,
    val eventLogSigner: EventLogSigner,
    val commit: Commit
) extends ExecutorFamily
