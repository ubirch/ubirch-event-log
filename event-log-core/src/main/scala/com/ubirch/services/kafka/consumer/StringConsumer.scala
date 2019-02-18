package com.ubirch.services.kafka.consumer

import java.util
import java.util.concurrent.atomic.AtomicInteger

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.EventLog
import com.ubirch.process.{ DefaultExecutor, Executor, WithConsumerRecordsExecutor }
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.services.lifeCycle.Lifecycle
import com.ubirch.util.Implicits.configsToProps
import com.ubirch.util.{ URLsHelper, UUIDHelper, VersionedLazyLogging }
import javax.inject._
import org.apache.kafka.clients.consumer._
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps

/**
  * Represents the ProcessResult implementation for a the string consumer.
  * @param consumerRecord Represents the data received in the poll from Kafka
  * @param eventLog Represents the event log type. It is here for informative purposes.
  */
case class PipeData(consumerRecord: ConsumerRecord[String, String], eventLog: Option[EventLog]) extends ProcessResult[String, String]

/**
  * Represents a concrete data type for a consumer runner of type Consumer[String, String]
  */
class StringConsumer extends ConsumerRunner[String, String]("consumer_runner_thread" + "_" + UUIDHelper.randomUUID) {

  override def process(consumerRecord: ConsumerRecord[String, String]): Future[ProcessResult[String, String]] = {
    getConsumerRecordsController.map(_.process(consumerRecord)).getOrElse(Future.failed(new Exception("Hey, I don't what what this is")))
  }

}

/**
  * Represents a concrete records controller for the string consumer.
  * This class can be thought of as a the glue for the consumer and the executor.
  * It defines the executor and the error exception handler and the error reporter.
  * @param defaultExecutor Represents the execution pipeline that processes the consumer records.
  * @param ec Represent the execution context for asynchronous processing.
  */
@Singleton
class DefaultConsumerRecordsController @Inject() (val defaultExecutor: DefaultExecutor)(implicit ec: ExecutionContext)
  extends ConsumerRecordsController[String, String]
  with WithConsumerRecordsExecutor[String, String]
  with LazyLogging {

  override def executor[A >: ProcessResult[String, String]]: Executor[ConsumerRecord[String, String], Future[PipeData]] = defaultExecutor.executor

  override def executorExceptionHandler[A >: ProcessResult[String, String]]: PartialFunction[Throwable, Future[PipeData]] = defaultExecutor.executorExceptionHandler

  override def reporter: Reporter = defaultExecutor.reporter

  override def process[A >: ProcessResult[String, String]](consumerRecord: ConsumerRecord[String, String]): Future[PipeData] = {
    executor(consumerRecord).recoverWith(executorExceptionHandler)
  }

}

/**
  * Represents a simple rebalance listener that can be plugged into the consumer.
  * @param consumer Represents an instance a consumer.
  * @tparam K Represents the type of the Key for the consumer.
  * @tparam V Represents the type of the Value for the consumer.
  */
class DefaultConsumerRebalanceListener[K, V](consumer: Consumer[K, V]) extends ConsumerRebalanceListener with VersionedLazyLogging {

  override val version: AtomicInteger = DefaultConsumerRebalanceListener.version

  override def onPartitionsRevoked(partitions: util.Collection[TopicPartition]): Unit = {
    val iterator = partitions.iterator().asScala
    iterator.foreach(x => logger.debug(s"onPartitionsRevoked: [${x.topic()}-${x.partition()}]"))
  }

  override def onPartitionsAssigned(partitions: util.Collection[TopicPartition]): Unit = {
    val iterator = partitions.iterator().asScala
    iterator.foreach(x => logger.debug(s"OnPartitionsAssigned: [${x.topic()}-${x.partition()}]"))
  }

}

/**
  * Represents the companion object for the Rebalance Listener
  */
object DefaultConsumerRebalanceListener {

  val version: AtomicInteger = new AtomicInteger(0)

  def apply[K, V](consumer: Consumer[K, V]): DefaultConsumerRebalanceListener[K, V] = {
    new DefaultConsumerRebalanceListener(consumer)
  }

}

/**
  * Represents a string consumer provider. Basically, it plugs in
  * configurations and shutdown hooks.
  * @param config Represents a config instance.
  * @param lifecycle Represents a lifecycle service instance to register shutdown hooks.
  * @param controller Represents a the records controllers for the consumer.
  * @param ec Represent the execution context for asynchronous processing.
  */
class DefaultStringConsumer @Inject() (
    config: Config,
    lifecycle: Lifecycle,
    controller: DefaultConsumerRecordsController
)(implicit ec: ExecutionContext) extends Provider[StringConsumer] with LazyLogging {

  import UUIDHelper._
  import com.ubirch.ConfPaths.Consumer._

  val bootstrapServers: String = URLsHelper.passThruWithCheck(config.getString(BOOTSTRAP_SERVERS))
  val topic: String = config.getString(TOPIC_PATH)
  val groupId: String = {
    val gid = config.getString(GROUP_ID_PATH)
    if (gid.isEmpty) "event_log_group_" + randomUUID
    else gid
  }

  def configs = Configs(
    bootstrapServers = bootstrapServers,
    groupId = groupId,
    enableAutoCommit = false,
    autoOffsetReset = OffsetResetStrategy.EARLIEST
  )

  val consumerImp = new StringConsumer

  private val consumerConfigured = {
    consumerImp.setTopics(Set(topic))
    consumerImp.setProps(configs)
    consumerImp.setKeyDeserializer(Some(new StringDeserializer()))
    consumerImp.setValueDeserializer(Some(new StringDeserializer()))
    consumerImp.setConsumerRebalanceListenerBuilder(Some(DefaultConsumerRebalanceListener.apply))
    consumerImp.setConsumerRecordsController(Some(controller))
    consumerImp
  }

  override def get(): StringConsumer = {
    consumerConfigured
  }

  val gracefulTimeout: Int = config.getInt(GRACEFUL_TIMEOUT_PATH)

  lifecycle.addStopHook { () =>
    logger.info("Shutting down Consumer: " + consumerConfigured.getName)
    Future.successful(consumerConfigured.shutdown(gracefulTimeout, java.util.concurrent.TimeUnit.SECONDS))
  }

}

