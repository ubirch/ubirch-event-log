package com.ubirch.services.kafka

import java.util.UUID

import com.google.inject.Provider
import com.typesafe.config.Config
import com.ubirch.services.lifeCycle.Lifecycle
import org.apache.kafka.clients.consumer.ConsumerRecords
import javax.inject._

import scala.concurrent.Future

class StringConsumer[R](
  val topic: String,
  configs: Configs,
  name: String,
  val maybeExecutor: Option[Executor[ConsumerRecords[String, String], R]])
    extends AbstractStringConsumer[R](name) {

  override val props: Map[String, AnyRef] = configs.props

}

class DefaultStringConsumerUnit @Inject() (config: Config, lifecycle: Lifecycle) extends Provider[StringConsumer[Unit]] {

  val topic = config.getString("eventLog.kafkaConsumer.topic")
  val groupId = config.getString("eventLog.kafkaConsumer.groupId")

  def threadName = topic + "_thread" + "_" + UUID.randomUUID()

  val configs = Configs(groupId = groupId)

  val wrapper = new Wrapper
  val nonEmpty = new FilterEmpty
  val logger = new Logger

  val executor = Option(wrapper andThen nonEmpty andThen logger)

  def consumer =
    new StringConsumer(
      topic,
      configs,
      threadName,
      executor)

  override def get(): StringConsumer[Unit] = consumer

  lifecycle.addStopHook { () ⇒
    Future.successful(consumer.shutdown(2, java.util.concurrent.TimeUnit.SECONDS))
  }

}

