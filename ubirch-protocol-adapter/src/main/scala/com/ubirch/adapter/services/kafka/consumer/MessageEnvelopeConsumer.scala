package com.ubirch.adapter.services.kafka.consumer

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ConsumerConfPaths
import com.ubirch.adapter.process.ExecutorFamily
import com.ubirch.adapter.util.Exceptions._
import com.ubirch.kafka.consumer._
import com.ubirch.kafka.util.ConfigProperties
import com.ubirch.kafka.{ EnvelopeDeserializer, MessageEnvelope }
import com.ubirch.models.{ Error, EventLog }
import com.ubirch.process.Executor
import com.ubirch.services.kafka.consumer.{ ConsumerRecordsManager, EventLogPipeData }
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.services.lifeCycle.Lifecycle
import com.ubirch.util.{ URLsHelper, UUIDHelper }
import javax.inject._
import org.apache.kafka.clients.consumer.{ ConsumerRecord, OffsetResetStrategy }
import org.apache.kafka.clients.producer.{ ProducerRecord, RecordMetadata }
import org.apache.kafka.common.serialization.StringDeserializer

import scala.concurrent.{ ExecutionContext, Future }

/**
  * Represents a convenience for handling and keeping data through the pipeline
  * @param consumerRecord Represents the consumer record read from kafka
  * @param eventLog Represents the EventLog created from the consumer record.
  * @param producerRecord Represents the Producer Record that is published back to kafka
  * @param recordMetadata Represents the response gotten from the publishing of the producer record.
  */
case class MessageEnvelopePipeData(
    override val consumerRecord: ConsumerRecord[String, MessageEnvelope],
    eventLog: Option[EventLog],
    producerRecord: Option[ProducerRecord[String, String]],
    recordMetadata: Option[RecordMetadata]
) extends EventLogPipeData[MessageEnvelope](consumerRecord, eventLog)

/**
  * Represents an Envelope Consumer.
  * @param ec Represents an execution context
  */
class MessageEnvelopeConsumer(implicit val ec: ExecutionContext) extends ConsumerRunner[String, MessageEnvelope](ConsumerRunner.name)

/**
  * Represents the Message Envelope Manager Description
  */
trait MessageEnvelopeConsumerRecordsManager extends ConsumerRecordsManager[String, MessageEnvelope] {
  val executorFamily: ExecutorFamily
}

/***
  * Represents an Concrete Message Envelope Manager
  * @param reporter Represents a reporter to send  errors to.
  * @param executorFamily Represents a group of executors that accomplish the global task
  * @param ec Represents an execution context
  */
@Singleton
class DefaultMessageEnvelopeManager @Inject() (val reporter: Reporter, val executorFamily: ExecutorFamily)(implicit ec: ExecutionContext)
  extends MessageEnvelopeConsumerRecordsManager
  with LazyLogging {

  import reporter.Types._

  type A = MessageEnvelopePipeData

  def executor: Executor[ConsumerRecord[String, MessageEnvelope], Future[MessageEnvelopePipeData]] = {
    executorFamily.eventLogFromConsumerRecord andThen executorFamily.createProducerRecord andThen executorFamily.commit
  }

  def executorExceptionHandler: PartialFunction[Throwable, Future[MessageEnvelopePipeData]] = {
    case e @ EventLogFromConsumerRecordException(_, pipeData) =>
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = pipeData.consumerRecord.value().toString))
      Future.successful(pipeData)
    case e @ CreateProducerRecordException(_, pipeData) =>
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = pipeData.consumerRecord.value().toString))
      Future.successful(pipeData)
    case e @ CommitException(_, pipeData) =>
      Future.failed(e)
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = pipeData.consumerRecord.value().toString))
      Future.successful(pipeData)
  }

}

/**
  * Represents a Message Envelope Consumer Configurator
  * @param config Represents a config object to read config values from
  * @param lifecycle Represents a lifecycle object to plug in shutdown routines
  * @param controller Represents a Message Envelope Records Controller
  * @param ec Represents an execution context
  */
class DefaultMessageEnvelopeConsumer @Inject() (
    config: Config,
    lifecycle: Lifecycle,
    controller: MessageEnvelopeConsumerRecordsManager
)(implicit ec: ExecutionContext)
  extends Provider[MessageEnvelopeConsumer]
  with ConsumerConfPaths
  with LazyLogging {

  import UUIDHelper._

  lazy val consumerConfigured = {
    val consumerImp = new MessageEnvelopeConsumer() with WithMetrics[String, MessageEnvelope]
    consumerImp.setUseAutoCommit(false)
    consumerImp.setTopics(Set(topic))
    consumerImp.setProps(configs)
    consumerImp.setKeyDeserializer(Some(new StringDeserializer()))
    consumerImp.setValueDeserializer(Some(EnvelopeDeserializer))
    consumerImp.setUseSelfAsRebalanceListener(true)
    consumerImp.setConsumerRecordsController(Some(controller))
    consumerImp
  }

  def gracefulTimeout: Int = config.getInt(GRACEFUL_TIMEOUT_PATH)

  def topic: String = config.getString(TOPIC_PATH)

  def configs: ConfigProperties = Configs(
    bootstrapServers = bootstrapServers,
    groupId = groupId,
    enableAutoCommit = false,
    autoOffsetReset = OffsetResetStrategy.EARLIEST
  )

  def bootstrapServers: String = URLsHelper.passThruWithCheck(config.getString(BOOTSTRAP_SERVERS))

  def groupId: String = {
    val gid = config.getString(GROUP_ID_PATH)
    if (gid.isEmpty) "adapter_event_log_group_" + randomUUID
    else gid
  }

  override def get(): MessageEnvelopeConsumer = consumerConfigured

  lifecycle.addStopHook { () =>
    logger.info("Shutting down Consumer: " + consumerConfigured.getName)
    Future.successful(consumerConfigured.shutdown(gracefulTimeout, java.util.concurrent.TimeUnit.SECONDS))
  }

}
