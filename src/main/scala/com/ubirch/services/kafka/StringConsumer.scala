package com.ubirch.services.kafka

import java.util.UUID

import com.google.inject.Provider
import com.typesafe.config.Config
import com.ubirch.ConfPaths
import com.ubirch.models.Events
import com.ubirch.services.lifeCycle.Lifecycle
import com.ubirch.util.Implicits.configsToProps
import javax.inject._
import org.apache.kafka.clients.consumer.{ ConsumerRecord, OffsetResetStrategy }
import org.apache.kafka.common.serialization.{ Deserializer, StringDeserializer }

import scala.concurrent.Future
import scala.language.implicitConversions

class StringConsumer(
  name: String,
  val executor: Executor[ConsumerRecord[String, String], Future[Unit]])
    extends AbstractConsumer[String, String, Unit](name) {

  val keyDeserializer: Deserializer[String] = new StringDeserializer()
  val valueDeserializer: Deserializer[String] = new StringDeserializer()

  override def isValueEmpty(v: String): Boolean = v.isEmpty
}

class DefaultStringConsumer @Inject() (
    config: Config,
    lifecycle: Lifecycle,
    events: Events,
    executor: DefaultExecutor) extends Provider[StringConsumer] {

  val topic: String = config.getString(ConfPaths.TOPIC_PATH)
  val groupId: String = config.getString(ConfPaths.GROUP_ID_PATH)
  val gracefulTimeout: Int = config.getInt(ConfPaths.GRACEFUL_TIMEOUT_PATH)
  val threadName: String = topic + "_thread" + "_" + UUID.randomUUID()
  val configs = Configs(groupId = groupId, autoOffsetReset = OffsetResetStrategy.EARLIEST)

  lazy val consumer = {
    new StringConsumer(threadName, executor.executor)
      .withTopic(topic)
      .withProps(configs)
  }

  override def get(): StringConsumer = consumer

  lifecycle.addStopHook { () ⇒
    Future.successful(consumer.shutdown(gracefulTimeout, java.util.concurrent.TimeUnit.SECONDS))
  }

}

