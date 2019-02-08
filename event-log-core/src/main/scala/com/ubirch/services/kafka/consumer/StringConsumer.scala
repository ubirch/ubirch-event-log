package com.ubirch.services.kafka.consumer

import com.google.inject.Provider
import com.typesafe.config.Config
import com.ubirch.ConfPaths
import com.ubirch.process.{ DefaultExecutor, Executor }
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.services.lifeCycle.Lifecycle
import com.ubirch.util.Implicits.configsToProps
import com.ubirch.util.{ URLsHelper, UUIDHelper }
import javax.inject._
import org.apache.kafka.clients.consumer.{ ConsumerRecord, OffsetResetStrategy }
import org.apache.kafka.common.serialization.{ Deserializer, StringDeserializer }

import scala.concurrent.{ ExecutionContext, Future }
import scala.language.implicitConversions

/**
  * Represents an implementation of the an abstract consumer whose
  * K and V values are String
  * @param name It is the name of the thread.
  * @param executor It is the executor that processes what to do with the read
  *                 messages from Kafka.
  * @param reporter It is the reporter to which the error messages are sent to for processing.
  * @param executorExceptionHandler It represents how error messages are handled. This includes
  *                                 how they are reported to the reporter.
  */
class StringConsumer(
    name: String,
    val executor: Executor[ConsumerRecord[String, String], Future[Unit]],
    val reporter: Reporter,
    val executorExceptionHandler: Exception => Future[Unit]
)(implicit ec: ExecutionContext)
  extends AbstractConsumer[String, String, Unit, Unit](name) {

  val keyDeserializer: Deserializer[String] = new StringDeserializer()
  val valueDeserializer: Deserializer[String] = new StringDeserializer()

  override def isValueEmpty(v: String): Boolean = v.isEmpty
}

/**
  * Represents a factory or provider for the StringConsumer for when they
  * are injected.
  * @param config Represents an inject Config object.
  * @param lifecycle Represents an injected lifeCycle for controlling shutdown hooks.
  * @param executor Represents the default injected family of composed executors that
  *                 control the processing of the read messages from Kafka.
  */
class DefaultStringConsumer @Inject() (
    config: Config,
    lifecycle: Lifecycle,
    executor: DefaultExecutor
)(implicit ec: ExecutionContext) extends Provider[StringConsumer] {

  import ConfPaths.Consumer._
  import UUIDHelper._

  val bootstrapServers: String = URLsHelper.passThruWithCheck(config.getString(BOOTSTRAP_SERVERS))
  val topic: String = config.getString(TOPIC_PATH)
  val groupId: String = {
    val gid = config.getString(GROUP_ID_PATH)
    if (gid.isEmpty) "event_log_group_" + randomUUID
    else gid
  }
  val gracefulTimeout: Int = config.getInt(GRACEFUL_TIMEOUT_PATH)
  val threadName: String = topic + "_thread" + "_" + randomUUID

  val configs = Configs(
    bootstrapServers = bootstrapServers,
    groupId = groupId,
    autoOffsetReset = OffsetResetStrategy.EARLIEST
  )

  private lazy val consumer = {
    new StringConsumer(
      threadName,
      executor.executor,
      executor.reporter,
      executor.executorExceptionHandler
    )
      .withTopic(topic)
      .withProps(configs)
  }

  override def get(): StringConsumer = consumer

  lifecycle.addStopHook { () =>
    Future.successful(consumer.shutdown(gracefulTimeout, java.util.concurrent.TimeUnit.SECONDS))
  }

}

