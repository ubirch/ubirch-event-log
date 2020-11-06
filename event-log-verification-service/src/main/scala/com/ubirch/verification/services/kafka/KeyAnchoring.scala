package com.ubirch.verification.services.kafka

import java.nio.charset.StandardCharsets

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.kafka.express.ExpressProducer
import com.ubirch.kafka.producer.{ ProducerRunner, WithProducerShutdownHook }
import com.ubirch.services.lifeCycle.Lifecycle
import com.ubirch.verification.ConfPaths.AcctEventPublishingConfPaths
import com.ubirch.verification.models.AcctEvent
import com.ubirch.verification.util.Exceptions.FailedKafkaPublish
import com.ubirch.verification.util.LookupJsonSupport
import javax.inject._
import monix.eval.Task
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.{ ByteArraySerializer, Serializer, StringSerializer }
import org.json4s.{ DefaultFormats, Formats }

import scala.concurrent.TimeoutException
import scala.concurrent.duration.{ FiniteDuration, _ }
import scala.language.postfixOps

trait AcctEventPublishing {
  def publish(value: AcctEvent): Task[RecordMetadata]
  def publishAsOpt(value: AcctEvent): Task[Option[RecordMetadata]]
  def publish(value: AcctEvent, timeout: FiniteDuration = 10 seconds): Task[(RecordMetadata, AcctEvent)]
}

abstract class AcctEventPublishingImpl(config: Config, lifecycle: Lifecycle)
  extends AcctEventPublishing
  with ExpressProducer[String, Array[Byte]]
  with WithProducerShutdownHook
  with LazyLogging {

  override val producerBootstrapServers: String = config.getString(AcctEventPublishingConfPaths.BOOTSTRAP_SERVERS)
  override val lingerMs: Int = config.getInt(AcctEventPublishingConfPaths.LINGER_MS)
  override val keySerializer: Serializer[String] = new StringSerializer
  override val valueSerializer: Serializer[Array[Byte]] = new ByteArraySerializer

  val producerTopic: String = config.getString(AcctEventPublishingConfPaths.TOPIC_PATH)

  private def earlyResponseIf(condition: Boolean)(response: Exception): Task[Unit] =
    if (condition) Task.raiseError(response) else Task.unit

  override def publish(value: AcctEvent): Task[RecordMetadata] = Task.defer {

    for {
      toPublish <- Task(value)
      toPublishAsToJson <- Task(LookupJsonSupport.ToJson(toPublish))
      toPublishAsString <- Task.delay(toPublishAsToJson.toString)
      toPublishAsBytes <- Task.delay(toPublishAsString.getBytes(StandardCharsets.UTF_8))
      rm <- Task.fromFuture {
        send(
          producerTopic,
          toPublishAsBytes
        )
      }
    } yield {
      rm
    }

  }

  override def publishAsOpt(value: AcctEvent): Task[Option[RecordMetadata]] = publish(value)
    .map(x => Option(x))
    .onErrorHandle {
      e =>
        logger.error("Error publishing AcctEvent to kafka, pk={} exception={} error_message", value, e.getClass.getName, e.getMessage)
        None
    }

  override def publish(value: AcctEvent, timeout: FiniteDuration): Task[(RecordMetadata, AcctEvent)] = {
    for {
      maybeRM <- publishAsOpt(value)
        .timeoutTo(timeout, Task.raiseError(FailedKafkaPublish(value, Option(new TimeoutException(s"failed_publish_timeout=${timeout.toString()}")))))
        .onErrorHandleWith(e => Task.raiseError(FailedKafkaPublish(value, Option(e))))
      _ = if (maybeRM.isEmpty) logger.error("failed_publish={}", value.toString)
      _ = if (maybeRM.isDefined) logger.info("publish_succeeded_for={}", value.identityId)
      _ <- earlyResponseIf(maybeRM.isEmpty)(FailedKafkaPublish(value, None))
    } yield {
      (maybeRM.get, value)
    }
  }

  lifecycle.addStopHook(hookFunc(production))

}

@Singleton
class DefaultAcctEventPublishing @Inject() (config: Config, lifecycle: Lifecycle)
  extends AcctEventPublishingImpl(config, lifecycle) {

  implicit val formats: Formats = DefaultFormats

  override lazy val production: ProducerRunner[String, Array[Byte]] = ProducerRunner(producerConfigs, Some(keySerializer), Some(valueSerializer))

}
