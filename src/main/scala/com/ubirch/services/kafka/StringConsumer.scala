package com.ubirch.services.kafka

import java.util.UUID

import com.google.inject.Provider
import com.typesafe.config.Config
import com.ubirch.ConfPaths
import com.ubirch.models.Events
import com.ubirch.services.lifeCycle.Lifecycle
import javax.inject._
import org.apache.kafka.clients.consumer.{ ConsumerRecords, OffsetResetStrategy }

import scala.concurrent.{ ExecutionContext, Future }

class StringConsumer[R](
  val topic: String,
  configs: Configs,
  name: String,
  val executor: Executor[ConsumerRecords[String, String], Future[R]])
    extends AbstractStringConsumer[R](name) {

  override val props: Map[String, AnyRef] = configs.props

}

class DefaultStringConsumerUnit @Inject() (
    config: Config,
    lifecycle: Lifecycle,
    events: Events,
    executor: DefaultExecutor) extends Provider[StringConsumer[Vector[Unit]]] {

  val topic: String = config.getString(ConfPaths.TOPIC_PATH)
  val groupId: String = config.getString(ConfPaths.GROUP_ID_PATH)
  val gracefulTimeout: Int = config.getInt(ConfPaths.GRACEFUL_TIMEOUT_PATH)

  def threadName: String = topic + "_thread" + "_" + UUID.randomUUID()

  val configs = Configs(groupId = groupId, autoOffsetReset = OffsetResetStrategy.EARLIEST)

  def consumer =
    new StringConsumer[Vector[Unit]](
      topic,
      configs,
      threadName,
      executor.executor)

  override def get(): StringConsumer[Vector[Unit]] = consumer

  lifecycle.addStopHook { () ⇒
    Future.successful(consumer.shutdown(gracefulTimeout, java.util.concurrent.TimeUnit.SECONDS))
  }

}

