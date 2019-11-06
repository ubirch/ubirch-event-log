package com.ubirch.encoder.services.kafka.consumer

import java.util.UUID

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.encoder.process.EncoderExecutor
import com.ubirch.kafka.consumer._
import com.ubirch.services.kafka.consumer.ConsumerCreator
import com.ubirch.services.lifeCycle.Lifecycle
import com.ubirch.util.UUIDHelper
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.serialization.{ ByteArrayDeserializer, StringDeserializer }
import org.json4s.JValue

import scala.concurrent.ExecutionContext

case class EncoderPipeData(consumerRecords: Vector[ConsumerRecord[String, Array[Byte]]], jValues: Vector[JValue]) extends ProcessResult[String, Array[Byte]] {
  override val id: UUID = UUIDHelper.randomUUID
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
    controller: EncoderExecutor
)(implicit val ec: ExecutionContext)
  extends Provider[BytesConsumer]
  with ConsumerCreator
  with WithConsumerShutdownHook
  with LazyLogging {

  lazy val consumerConfigured = {
    logger.info(configs.props.toString)
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

  lifecycle.addStopHook(hookFunc(gracefulTimeout, consumerConfigured))

}
