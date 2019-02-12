package com.ubirch.services.kafka.consumer

import java.util

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.process.{ DefaultExecutor, Executor, WithConsumerRecordsExecutor }
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.services.lifeCycle.Lifecycle
import com.ubirch.util.Exceptions.NeedForShutDownException
import com.ubirch.util.Implicits.configsToProps
import com.ubirch.util.{ URLsHelper, UUIDHelper }
import javax.inject._
import org.apache.kafka.clients.consumer._
import org.apache.kafka.clients.consumer.internals.NoOpConsumerRebalanceListener
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringDeserializer

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

class StringConsumer extends ConsumerRunner[String, String]("consumer_runner_thread" + "_" + UUIDHelper.randomUUID) {

  override def process(consumerRecords: ConsumerRecords[String, String], iterator: Iterator[ConsumerRecord[String, String]]): Unit = {
    consumerRecordsController.foreach(_.process(consumerRecords, iterator))
  }

  override def isValueEmpty(v: String): Boolean = v.isEmpty

}

@Singleton
class ConsumerRecordsControllerImp @Inject() (val defaultExecutor: DefaultExecutor)(implicit ec: ExecutionContext)
  extends ConsumerRecordsController[String, String]
  with WithConsumerRecordsExecutor[String, String, Future[Unit], Future[Unit]] {

  override val executor: Executor[ConsumerRecord[String, String], Future[Unit]] = defaultExecutor.executor
  override val executorExceptionHandler: Exception => Future[Unit] = defaultExecutor.executorExceptionHandler
  override val reporter: Reporter = defaultExecutor.reporter

  override def process(consumerRecords: ConsumerRecords[String, String], iterator: Iterator[ConsumerRecord[String, String]]): Unit = {

    val mappedIterator = iterator.map(cr => (cr, executor))

    def continue = mappedIterator.hasNext

    while (continue) {
      val (consumerRecord, executor) = mappedIterator.next()
      val res = executor(consumerRecord)
        .map(x => Right(Some(x)))
        .recoverWith {
          case e: Exception =>
            executorExceptionHandler(e).map(x => Left(Some(x)))
          case e =>
            Future.failed(NeedForShutDownException("Exception not handled.", e.getMessage))
        }

      res.onComplete {
        case Success(_) =>
        case Failure(exception) => somethingWentWrong(exception)
      }

      checkIfSomethingWentWrong()

    }

  }

}

class ConsumerImpRebalanceListener() extends ConsumerRebalanceListener {

  override def onPartitionsRevoked(partitions: util.Collection[TopicPartition]): Unit = ???

  override def onPartitionsAssigned(partitions: util.Collection[TopicPartition]): Unit = ???
}

class DefaultStringConsumer @Inject() (
    config: Config,
    lifecycle: Lifecycle,
    controller: ConsumerRecordsControllerImp
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
    consumerImp.setConsumerRebalanceListener(Some(new NoOpConsumerRebalanceListener))
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

