package com.ubirch.services.kafka.consumer

import java.util.UUID

import com.google.inject.Provider
import com.typesafe.config.Config
import com.ubirch.ConfPaths
import com.ubirch.process.{ DefaultExecutor, Executor }
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.services.lifeCycle.Lifecycle
import com.ubirch.util.Implicits.configsToProps
import javax.inject._
import org.apache.kafka.clients.consumer.{ ConsumerRecord, OffsetResetStrategy }
import org.apache.kafka.common.serialization.{ Deserializer, StringDeserializer }

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.language.implicitConversions

class StringConsumer(
  name: String,
  val executor: Executor[ConsumerRecord[String, String], Future[Unit]],
  val reporter: Reporter,
  val executorExceptionHandler: Exception ⇒ Unit)
    extends AbstractConsumer[String, String, Unit](name) {

  val keyDeserializer: Deserializer[String] = new StringDeserializer()
  val valueDeserializer: Deserializer[String] = new StringDeserializer()

  override def isValueEmpty(v: String): Boolean = v.isEmpty
}

class DefaultStringConsumer @Inject() (
    config: Config,
    lifecycle: Lifecycle,
    executor: DefaultExecutor) extends Provider[StringConsumer] {

  import ConfPaths.Consumer._

  val bootstrapServers: String = config.getStringList(BOOTSTRAP_SERVERS).asScala.mkString("")
  val topic: String = config.getString(TOPIC_PATH)
  val groupId: String = config.getString(GROUP_ID_PATH)
  val gracefulTimeout: Int = config.getInt(GRACEFUL_TIMEOUT_PATH)
  val threadName: String = topic + "_thread" + "_" + UUID.randomUUID()

  val configs = Configs(
    bootstrapServers = bootstrapServers,
    groupId = groupId,
    autoOffsetReset = OffsetResetStrategy.EARLIEST)

  lazy val consumer = {
    new StringConsumer(
      threadName,
      executor.executor,
      executor.reporter,
      executor.executorExceptionHandler).withTopic(topic)
      .withProps(configs)
  }

  override def get(): StringConsumer = consumer

  lifecycle.addStopHook { () ⇒
    Future.successful(consumer.shutdown(gracefulTimeout, java.util.concurrent.TimeUnit.SECONDS))
  }

}

