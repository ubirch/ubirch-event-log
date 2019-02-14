package com.ubirch.services.kafka.consumer

import java.util

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.EventLog
import com.ubirch.process.{ DefaultExecutor, Executor, WithConsumerRecordsExecutor }
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.services.lifeCycle.Lifecycle
import com.ubirch.util.Exceptions.NeedForShutDownException
import com.ubirch.util.Implicits.configsToProps
import com.ubirch.util.{ URLsHelper, UUIDHelper }
import javax.inject._
import org.apache.kafka.clients.consumer._
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps

case class PipeData(consumerRecord: ConsumerRecord[String, String], eventLog: Option[EventLog]) extends ProcessResult[String, String]

class StringConsumer extends ConsumerRunner[String, String]("consumer_runner_thread" + "_" + UUIDHelper.randomUUID) {

  override def process(consumerRecord: ConsumerRecord[String, String]): Future[ProcessResult[String, String]] = {
    getConsumerRecordsController.map(_.process(consumerRecord)).getOrElse(Future.failed(new Exception("Hey, I don't what what this is")))
  }

  override def isValueEmpty(v: String): Boolean = v.isEmpty

}

@Singleton
class DefaultConsumerRecordsController @Inject() (val defaultExecutor: DefaultExecutor)(implicit ec: ExecutionContext)
  extends ConsumerRecordsController[String, String]
  with WithConsumerRecordsExecutor[String, String]
  with LazyLogging {

  override def executor[A >: ProcessResult[String, String]]: Executor[ConsumerRecord[String, String], Future[PipeData]] = defaultExecutor.executor

  override def executorExceptionHandler[A >: ProcessResult[String, String]]: PartialFunction[Throwable, Future[PipeData]] = defaultExecutor.executorExceptionHandler

  override val reporter: Reporter = defaultExecutor.reporter

  override def process[A >: ProcessResult[String, String]](consumerRecord: ConsumerRecord[String, String]): Future[PipeData] = {
    executor(consumerRecord)
      .recoverWith(executorExceptionHandler)
      .recoverWith {
        case e =>
          Future.failed(NeedForShutDownException("Exception not handled.", e.getMessage))
      }
  }

}

class DefaultConsumerRebalanceListener[K, V](consumer: Consumer[K, V]) extends ConsumerRebalanceListener {

  override def onPartitionsRevoked(partitions: util.Collection[TopicPartition]): Unit = {
    val iterator = partitions.iterator().asScala
    iterator.foreach(x => println(x.partition() + " " + x.topic()))
  }

  override def onPartitionsAssigned(partitions: util.Collection[TopicPartition]): Unit = {
    val iterator = partitions.iterator().asScala
    iterator.foreach(x => println(x.partition() + " " + x.topic()))
  }

}

object DefaultConsumerRebalanceListener {
  def apply[K, V](consumer: Consumer[K, V]): DefaultConsumerRebalanceListener[K, V] =
    new DefaultConsumerRebalanceListener(consumer)
}

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

  val configs = Configs(
    bootstrapServers = bootstrapServers,
    groupId = groupId,
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

