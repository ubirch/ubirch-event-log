package com.ubirch.services.kafka.consumer

import java.util.UUID

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ConsumerConfPaths
import com.ubirch.kafka.consumer._
import com.ubirch.kafka.util.ConfigProperties
import com.ubirch.kafka.{ EnvelopeDeserializer, MessageEnvelope }
import com.ubirch.models.EventLog
import com.ubirch.process.{ Executor, ExecutorFamily, WithConsumerRecordsExecutor }
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.services.lifeCycle.Lifecycle
import com.ubirch.util.{ URLsHelper, UUIDHelper }
import javax.inject._
import org.apache.kafka.clients.consumer.{ ConsumerRecord, OffsetResetStrategy }
import org.apache.kafka.common.serialization.StringDeserializer

import scala.concurrent.{ ExecutionContext, Future }

case class MessageEnvelopePipeData(
    override val consumerRecord: ConsumerRecord[String, MessageEnvelope],
    eventLog: Option[EventLog]
) extends EventLogPipeData[MessageEnvelope](consumerRecord, eventLog)

class MessageEnvelopeConsumer(implicit val ec: ExecutionContext) extends ConsumerRunner[String, MessageEnvelope](ConsumerRunner.name)

trait MessageEnvelopeConsumerRecordsExecutor extends WithConsumerRecordsExecutor[String, MessageEnvelope]

trait MessageEnvelopeConsumerRecordsController extends ConsumerRecordsController[String, MessageEnvelope]

trait MessageEnvelopeConsumerRecordsManager extends MessageEnvelopeConsumerRecordsController with MessageEnvelopeConsumerRecordsExecutor {
  val executorFamily: ExecutorFamily
}

@Singleton
class DefaultMessageEnvelopeManager @Inject() (val reporter: Reporter, val executorFamily: ExecutorFamily)(implicit ec: ExecutionContext)
  extends MessageEnvelopeConsumerRecordsManager
  with LazyLogging {

  override type A = MessageEnvelopePipeData

  override def executor: Executor[ConsumerRecord[String, MessageEnvelope], Future[MessageEnvelopePipeData]] = {
    executorFamily.eventLoggerExecutor
  }

  override def executorExceptionHandler: PartialFunction[Throwable, Future[MessageEnvelopePipeData]] = ???

  override def process(consumerRecord: ConsumerRecord[String, MessageEnvelope]): Future[MessageEnvelopePipeData] = {
    executor(consumerRecord).recoverWith(executorExceptionHandler)
  }
}

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
