package com.ubirch.encoder.services.kafka.consumer

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ConsumerConfPaths
import com.ubirch.encoder.process.ExecutorFamily
import com.ubirch.encoder.util.Exceptions._
import com.ubirch.kafka.consumer._
import com.ubirch.kafka.util.ConfigProperties
import com.ubirch.models.{ Error, EventLog }
import com.ubirch.process.Executor
import com.ubirch.services.kafka.consumer.{ ConsumerRecordsManager, EventLogPipeData, WithPublishingData }
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.services.lifeCycle.Lifecycle
import com.ubirch.util.{ Decision, URLsHelper, UUIDHelper }
import javax.inject._
import org.apache.kafka.clients.consumer.{ ConsumerRecord, OffsetResetStrategy }
import org.apache.kafka.clients.producer.{ ProducerRecord, RecordMetadata }
import org.apache.kafka.common.serialization.{ ByteArrayDeserializer, StringDeserializer }
import org.json4s.JValue
import org.json4s.JsonAST.JString

import scala.concurrent.{ ExecutionContext, Future }

/**
  * Represents a convenience for handling and keeping data through the pipeline
  * @param consumerRecords Represents the consumer record read from kafka
  * @param eventLog Represents the EventLog created from the consumer record.
  * @param producerRecord Represents the Producer Record that is published back to kafka
  * @param recordMetadata Represents the response gotten from the publishing of the producer record.
  */
case class EncoderPipeData(
    consumerRecords: Vector[ConsumerRecord[String, Array[Byte]]],
    messageJValue: Option[JValue],
    eventLog: Option[EventLog],
    producerRecord: Option[Decision[ProducerRecord[String, String]]],
    recordMetadata: Option[RecordMetadata]
)
  extends EventLogPipeData[Array[Byte]] with WithPublishingData[String]

/**
  * Represents the Message Envelope Manager Description
  */
trait EncoderConsumerRecordsManager extends ConsumerRecordsManager[String, Array[Byte]] {
  val executorFamily: ExecutorFamily
}

/***
  * Represents an Concrete Message Envelope Manager
  * @param reporter Represents a reporter to send  errors to.
  * @param executorFamily Represents a group of executors that accomplish the global task
  * @param ec Represents an execution context
  */
@Singleton
class DefaultEncoderManager @Inject() (val reporter: Reporter, val executorFamily: ExecutorFamily)(implicit ec: ExecutionContext)
  extends EncoderConsumerRecordsManager
  with LazyLogging {

  import org.json4s.jackson.JsonMethods._
  import reporter.Types._

  type A = EncoderPipeData

  def executor: Executor[Vector[ConsumerRecord[String, Array[Byte]]], Future[EncoderPipeData]] = {
    executorFamily.jValueFromConsumerRecord andThen
      executorFamily.eventLogFromConsumerRecord andThen
      executorFamily.eventLogSigner andThen
      executorFamily.createProducerRecord andThen
      executorFamily.commit
  }

  def executorExceptionHandler: PartialFunction[Throwable, Future[EncoderPipeData]] = {
    case e @ JValueFromConsumerRecordException(_, pipeData) =>
      logger.debug("EventLogFromConsumerRecordException: " + e.getMessage)
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = pipeData.toString))
      Future.successful(pipeData)
    case e @ EventLogFromConsumerRecordException(_, pipeData) =>
      logger.debug("EventLogFromConsumerRecordException: " + e.getMessage)
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = compact(pipeData.messageJValue.getOrElse(JString("No JValue")))))
      Future.successful(pipeData)
    case e @ SigningEventLogException(_, pipeData) =>
      logger.debug("SigningEventLogException: " + e.getMessage)
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = pipeData.eventLog.map(x => x.toJson).getOrElse("No EventLog")))
      Future.successful(pipeData)
    case e @ CreateProducerRecordException(_, pipeData) =>
      logger.debug("CreateProducerRecordException: " + e.getMessage)
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = pipeData.eventLog.map(x => x.toJson).getOrElse("No EventLog")))
      Future.successful(pipeData)
    case e @ CommitException(_, pipeData) =>
      logger.debug("CommitException: " + e.getMessage)
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = pipeData.eventLog.map(x => x.toJson).getOrElse("No EventLog")))
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
class DefaultEncoderConsumer @Inject() (
    config: Config,
    lifecycle: Lifecycle,
    controller: EncoderConsumerRecordsManager
)(implicit ec: ExecutionContext)
  extends Provider[BytesConsumer]
  with ConsumerConfPaths
  with LazyLogging {

  import UUIDHelper._

  lazy val consumerConfigured = {
    val consumerImp = new BytesConsumer() with WithMetrics[String, Array[Byte]]
    consumerImp.setUseAutoCommit(false)
    consumerImp.setTopics(topic)
    consumerImp.setProps(configs)
    consumerImp.setKeyDeserializer(Some(new StringDeserializer()))
    consumerImp.setValueDeserializer(Some(new ByteArrayDeserializer()))
    consumerImp.setUseSelfAsRebalanceListener(true)
    consumerImp.setConsumerRecordsController(Some(controller))
    consumerImp
  }

  def gracefulTimeout: Int = config.getInt(GRACEFUL_TIMEOUT_PATH)

  def topic: Set[String] = config.getString(TOPIC_PATH).split(",").toSet.filter(_.nonEmpty).map(_.trim)

  def configs: ConfigProperties = Configs(
    bootstrapServers = bootstrapServers,
    groupId = groupId,
    enableAutoCommit = false,
    autoOffsetReset = OffsetResetStrategy.EARLIEST
  )

  def bootstrapServers: String = URLsHelper.passThruWithCheck(config.getString(BOOTSTRAP_SERVERS))

  def groupId: String = {
    val gid = config.getString(GROUP_ID_PATH)
    if (gid.isEmpty) "encoder_event_log_group_" + randomUUID
    else gid
  }

  override def get(): BytesConsumer = consumerConfigured

  lifecycle.addStopHook { () =>
    logger.info("Shutting down Consumer: " + consumerConfigured.getName)
    Future.successful(consumerConfigured.shutdown(gracefulTimeout, java.util.concurrent.TimeUnit.SECONDS))
  }

}
