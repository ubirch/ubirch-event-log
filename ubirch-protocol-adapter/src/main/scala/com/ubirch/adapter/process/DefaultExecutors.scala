package com.ubirch.adapter.process

import java.util.concurrent.{ Future => JavaFuture }

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ProducerConfPaths
import com.ubirch.adapter.services.kafka.consumer.MessageEnvelopePipeData
import com.ubirch.adapter.util.Exceptions._
import com.ubirch.kafka.MessageEnvelope
import com.ubirch.kafka.producer.StringProducer
import com.ubirch.models.EventLog
import com.ubirch.process.Executor
import com.ubirch.util.Implicits.enrichedConfig
import com.ubirch.util.{ EventLogJsonSupport, FutureHelper, ProducerRecordHelper }
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.{ ProducerRecord, RecordMetadata }

import scala.concurrent.{ ExecutionContext, Future }

/**
  * Represents an executor that converts a consumer record of type String, MessageEnvelope into
  * an EventLog and wraps these values into the pipeline data.
  * @param ec Represents an execution context
  */
class EventLogFromConsumerRecord @Inject() (implicit ec: ExecutionContext)
  extends Executor[ConsumerRecord[String, MessageEnvelope], Future[MessageEnvelopePipeData]]
  with LazyLogging {

  import org.json4s.jackson.JsonMethods._

  override def apply(v1: ConsumerRecord[String, MessageEnvelope]): Future[MessageEnvelopePipeData] = Future {
    val result: MessageEnvelopePipeData = try {
      val payload = fromJsonNode(v1.value().ubirchPacket.getPayload)
      val eventLog = EventLog("EventLogFromConsumerRecord", "UPA", payload)
      MessageEnvelopePipeData(v1, Some(eventLog), None, None)
    } catch {
      case _: Exception =>
        throw EventLogFromConsumerRecordException("Error Parsing Into Event Log", MessageEnvelopePipeData(v1, None, None, None))
    }

    result
  }

}

/**
  * Represents an executor that creates the producer record object that will be eventually published to Kafka
  * @param config Represents a config object to read config values from
  * @param ec Represents an execution context
  */
class CreateProducerRecord @Inject() (config: Config)(implicit ec: ExecutionContext)
  extends Executor[Future[MessageEnvelopePipeData], Future[MessageEnvelopePipeData]]
  with ProducerConfPaths {
  override def apply(v1: Future[MessageEnvelopePipeData]): Future[MessageEnvelopePipeData] = {

    v1.map { v1 =>

      try {

        val topic = config.getStringAsOption(TOPIC_PATH).getOrElse("com.ubirch.eventlog")

        val output = v1.eventLog
          .map(EventLogJsonSupport.ToJson[EventLog])
          .map(x => ProducerRecordHelper.toRecord(topic, v1.id.toString, x.toString, Map.empty))
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
  * @param stringProducer Represents a producer.
  * @param config Represents a config object to read config values from
  * @param ec Represents an execution context
  */
class Commit @Inject() (stringProducer: StringProducer, config: Config)(implicit ec: ExecutionContext) extends Executor[Future[MessageEnvelopePipeData], Future[MessageEnvelopePipeData]] {

  def commit(record: ProducerRecord[String, String]): JavaFuture[RecordMetadata] = {
    stringProducer.getProducerOrCreate.send(record)
  }

  val futureHelper = new FutureHelper()

  override def apply(v1: Future[MessageEnvelopePipeData]): Future[MessageEnvelopePipeData] = {

    v1.flatMap { v1 =>

      try {

        v1.producerRecord
          .map(x => commit(x))
          .map(x => futureHelper.fromJavaFuture(x))
          .map(x => x.map(y => v1.copy(recordMetadata = Some(y))))
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
    val commit: Commit
) extends ExecutorFamily
