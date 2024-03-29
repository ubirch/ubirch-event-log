package com.ubirch.chainer.services.kafka.consumer

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ConsumerConfPaths
import com.ubirch.chainer.models.Chainer
import com.ubirch.chainer.process.ExecutorFamily
import com.ubirch.chainer.util._
import com.ubirch.models.{ Error, EventLog }
import com.ubirch.process.Executor
import com.ubirch.services.kafka.consumer.{ EventLogsPipeData, StringConsumerRecordsManager }
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.services.metrics.{ Counter, DefaultFailureCounter }
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.RecordMetadata

import scala.concurrent.{ ExecutionContext, Future }

/**
  * Represents a data that is used throughout the pipeline
  * @param consumerRecords Represents the kafka consumer records
  * @param eventLogs Represents the incoming event logs.
  * @param chainers Represents the chainers that have be used to create a tree
  * @param treeEventLogs Represents the generated trees are event logs
  * @param recordsMetadata Represents the response for sending with Kafka
  */
case class ChainerPipeData(
    consumerRecords: Vector[ConsumerRecord[String, String]],
    eventLogs: Vector[EventLog],
    chainers: Vector[Chainer[EventLog, String, String]],
    treeEventLogs: Vector[EventLog],
    recordsMetadata: Vector[RecordMetadata]
)
  extends EventLogsPipeData[String]

/**
  * Represents a default implementation of a consumer records manager used to assamble the pipeline of executors.
  * @param reporter Represents a reporter for errors
  * @param executorFamily Represents the family of executors for this manager
  * @param failureCounter Represents a Prometheus counter for failures
  * @param config Represents the configuration object
  * @param ec Represents the execution context for this manager.
  */
@Singleton
class DefaultChainerManager @Inject() (
    val reporter: Reporter,
    executorFamily: ExecutorFamily,
    @Named(DefaultFailureCounter.name) failureCounter: Counter,
    config: Config
)(implicit ec: ExecutionContext)
  extends StringConsumerRecordsManager
  with LazyLogging {

  import reporter.Types._

  lazy val metricsSubNamespace: String = config.getString(ConsumerConfPaths.METRICS_SUB_NAMESPACE)

  type A = ChainerPipeData

  def executor: Executor[Vector[ConsumerRecord[String, String]], Future[ChainerPipeData]] = {
    executorFamily.filterEmpty andThen
      executorFamily.eventLogParser andThen
      executorFamily.treeCreatorExecutor andThen
      executorFamily.commit
  }

  def executorExceptionHandler: PartialFunction[Throwable, Future[ChainerPipeData]] = {
    case e: EmptyValueException =>
      logger.error("EmptyValueException: " + e.getMessage)
      failureCounter.counter.labels(metricsSubNamespace).inc()
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name))
      Future.successful(e.pipeData)
    case e: ParsingIntoEventLogException =>
      logger.error("ParsingIntoEventLogException: " + e.getMessage)
      failureCounter.counter.labels(metricsSubNamespace).inc()
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = e.pipeData.consumerRecords.headOption.map(_.value()).getOrElse("No value")))
      Future.successful(e.pipeData)
    case e: SigningEventLogException =>
      logger.error("SigningEventLogException: " + e.getMessage)
      failureCounter.counter.labels(metricsSubNamespace).inc()
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = e.pipeData.consumerRecords.headOption.map(_.value()).getOrElse("No value")))
      Future.successful(e.pipeData)
    case e @ TreeCreatorExecutorException(_, pipeData) =>
      logger.error("CreateProducerRecordException: " + e.getMessage)
      failureCounter.counter.labels(metricsSubNamespace).inc()
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = e.pipeData.consumerRecords.headOption.map(_.value()).getOrElse("No value")))
      Future.successful(pipeData)
    case e @ CommitException(_, pipeData) =>
      //TODO: should we just retry the whole loop?
      logger.error("CommitException: " + e.getMessage)
      failureCounter.counter.labels(metricsSubNamespace).inc()
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = e.pipeData.consumerRecords.headOption.map(_.value()).getOrElse("No value")))
      Future.successful(pipeData)
  }

}
