package com.ubirch.services.kafka.consumer

import java.util
import java.util.concurrent.atomic.AtomicInteger

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ConsumerConfPaths
import com.ubirch.kafka.consumer._
import com.ubirch.kafka.util.Exceptions.NeedForPauseException
import com.ubirch.kafka.util.VersionedLazyLogging
import com.ubirch.models.{ Error, EventLog }
import com.ubirch.process._
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.services.lifeCycle.Lifecycle
import com.ubirch.services.metrics.{ Counter, DefaultFailureCounter }
import com.ubirch.util.Exceptions._
import javax.inject._
import org.apache.kafka.clients.consumer._
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps

/**
  * Represents the ProcessResult implementation for a the string consumer.
  *
  * @param consumerRecords Represents the data received in the poll from Kafka
  * @param eventLog        Represents the event log type. It is here for informative purposes.
  */
case class PipeData private (consumerRecords: Vector[ConsumerRecord[String, String]], eventLog: Option[EventLog]) extends EventLogPipeData[String]

object PipeData {
  def apply(consumerRecord: ConsumerRecord[String, String], eventLog: Option[EventLog]): PipeData = PipeData(Vector(consumerRecord), eventLog)
}

/**
  * Represents a String Consumer Record Controller with an Executor Pipeline
  * This class can be thought of as a the glue for the consumer and the executor.
  */
trait StringConsumerRecordsManager extends ConsumerRecordsManager[String, String]

/**
  * Represents a concrete records controller for the string consumer.
  * This class can be thought of as a the glue for the consumer and the executor.
  * It defines the executor and the error exception handler and the error reporter.
  *
  * @param ec Represent the execution context for asynchronous processing.
  */
@Singleton
class DefaultConsumerRecordsManager @Inject() (
    val reporter: Reporter,
    val executorFamily: ExecutorFamily,
    @Named(DefaultFailureCounter.name) failureCounter: Counter,
    config: Config
)(implicit ec: ExecutionContext)
  extends StringConsumerRecordsManager
  with LazyLogging {

  import reporter.Types._

  lazy val metricsSubNamespace: String = config.getString(ConsumerConfPaths.METRICS_SUB_NAMESPACE)

  type A = PipeData

  def executor: Executor[Vector[ConsumerRecord[String, String]], Future[PipeData]] = {
    executorFamily.loggerExecutor
  }

  def executorExceptionHandler: PartialFunction[Throwable, Future[PipeData]] = {
    case e: EmptyValueException =>
      logger.error("EmptyValueException: ", e)
      failureCounter.counter.labels(metricsSubNamespace).inc()
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name))
      Future.successful(e.pipeData)
    case e: ParsingIntoEventLogException =>
      logger.error("ParsingIntoEventLogException: ", e)
      failureCounter.counter.labels(metricsSubNamespace).inc()
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = e.pipeData.consumerRecords.headOption.map(_.value()).getOrElse("No value")))
      Future.successful(e.pipeData)
    case e: SigningEventLogException =>
      logger.error("SigningEventLogException: ", e)
      failureCounter.counter.labels(metricsSubNamespace).inc()
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = e.pipeData.consumerRecords.headOption.map(_.value()).getOrElse("No value")))
      Future.successful(e.pipeData)
    case e: StoringIntoEventLogException =>
      logger.error("StoringIntoEventLogException: ", e)
      failureCounter.counter.labels(metricsSubNamespace).inc()
      reporter.report(
        Error(
          id = e.pipeData.eventLog.map(_.id).getOrElse(uuid.toString),
          message = e.getMessage,
          exceptionName = e.name,
          value = e.pipeData.eventLog.toString
        )
      )

      val res = e.pipeData.eventLog.map { _ =>
        Future.failed(NeedForPauseException("Requesting Pause", e.getMessage))
      }.getOrElse {
        Future.successful(e.pipeData)
      }

      res
  }

}

/**
  * Represents a simple rebalance listener that can be plugged into the consumer.
  *
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
  *
  * @param config     Represents a config instance.
  * @param lifecycle  Represents a lifecycle service instance to register shutdown hooks.
  * @param controller Represents a the records controllers for the consumer.
  * @param ec         Represent the execution context for asynchronous processing.
  */
class DefaultStringConsumer @Inject() (
    val config: Config,
    lifecycle: Lifecycle,
    controller: StringConsumerRecordsManager
)(implicit ec: ExecutionContext)
  extends Provider[StringConsumer]
  with ConsumerCreator
  with WithConsumerShutdownHook {

  lazy val consumerConfigured = {
    val consumerImp = StringConsumer.emptyWithMetrics(metricsSubNamespace)
    consumerImp.setUseAutoCommit(false)
    consumerImp.setTopics(consumerTopics)
    consumerImp.setProps(consumerConfigs)
    consumerImp.setKeyDeserializer(Some(new StringDeserializer()))
    consumerImp.setValueDeserializer(Some(new StringDeserializer()))
    consumerImp.setConsumerRebalanceListenerBuilder(Some(DefaultConsumerRebalanceListener.apply))
    consumerImp.setConsumerRecordsController(Some(controller))
    consumerImp
  }

  override def consumerGroupIdOnEmpty: String = "event_log_group"

  override def get(): StringConsumer = consumerConfigured

  lifecycle.addStopHook(hookFunc(consumerGracefulTimeout, consumerConfigured))

}

