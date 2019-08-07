package com.ubirch.encoder.services.kafka.consumer

import java.util.UUID

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.encoder.process.ExecutorFamily
import com.ubirch.encoder.util.Exceptions._
import com.ubirch.kafka.consumer._
import com.ubirch.models.Error
import com.ubirch.process.Executor
import com.ubirch.services.kafka.consumer.{ ConsumerCreator, ConsumerRecordsManager }
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.services.lifeCycle.Lifecycle
import com.ubirch.services.metrics.{ Counter, DefaultConsumerRecordsManagerCounter }
import com.ubirch.util.UUIDHelper
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.{ ByteArrayDeserializer, StringDeserializer }
import org.json4s.JValue

import scala.concurrent.{ ExecutionContext, Future }

case class EncoderPipeData(consumerRecords: Vector[ConsumerRecord[String, Array[Byte]]], jValues: Vector[JValue]) extends ProcessResult[String, Array[Byte]] {
  override val id: UUID = UUIDHelper.randomUUID
}

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
class DefaultEncoderManager @Inject() (
    val reporter: Reporter,
    val executorFamily: ExecutorFamily,
    @Named(DefaultConsumerRecordsManagerCounter.name) counter: Counter
)(implicit ec: ExecutionContext)
  extends EncoderConsumerRecordsManager
  with LazyLogging {

  import org.json4s.jackson.JsonMethods._
  import reporter.Types._

  type A = EncoderPipeData

  val executor: Executor[Vector[ConsumerRecord[String, Array[Byte]]], Future[EncoderPipeData]] = {
    executorFamily.encoderExecutor
  }

  val executorExceptionHandler: PartialFunction[Throwable, Future[EncoderPipeData]] = {
    case e @ EncodingException(_, pipeData) =>
      logger.error("EncodingException: " + e.getMessage)
      counter.counter.labels("EncodingException").inc()
      val value = pipeData.jValues.headOption.map(x => compact(x)).getOrElse("No Value")
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = value))
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
    val config: Config,
    lifecycle: Lifecycle,
    controller: EncoderConsumerRecordsManager
)(implicit ec: ExecutionContext)
  extends Provider[BytesConsumer]
  with ConsumerCreator
  with LazyLogging {

  lazy val consumerConfigured = {
    val consumerImp = BytesConsumer.emptyWithMetrics(metricsSubNamespace)
    consumerImp.setUseAutoCommit(false)
    consumerImp.setTopics(topics)
    consumerImp.setProps(configs)
    consumerImp.setKeyDeserializer(Some(new StringDeserializer()))
    consumerImp.setValueDeserializer(Some(new ByteArrayDeserializer()))
    consumerImp.setUseSelfAsRebalanceListener(true)
    consumerImp.setConsumerRecordsController(Some(controller))
    consumerImp
  }

  override def groupIdOnEmpty: String = "encoder_event_log_group"

  override def get(): BytesConsumer = consumerConfigured

  lifecycle.addStopHook { () =>
    logger.info("Shutting down Consumer: " + consumerConfigured.getName)
    Future.successful(consumerConfigured.shutdown(gracefulTimeout, java.util.concurrent.TimeUnit.SECONDS))
  }

}
