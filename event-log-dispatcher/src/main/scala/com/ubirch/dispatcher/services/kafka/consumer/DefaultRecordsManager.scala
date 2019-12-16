package com.ubirch.dispatcher.services.kafka.consumer

import java.util.UUID

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ConsumerConfPaths
import com.ubirch.dispatcher.process.ExecutorFamily
import com.ubirch.dispatcher.util.Exceptions.{ CommitException, CreateProducerRecordException, EmptyValueException, ParsingIntoEventLogException }
import com.ubirch.kafka.consumer.ProcessResult
import com.ubirch.models.{ Error, EventLog }
import com.ubirch.process.Executor
import com.ubirch.services.kafka.consumer.StringConsumerRecordsManager
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.services.metrics.{ Counter, DefaultFailureCounter }
import com.ubirch.util.UUIDHelper
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.concurrent.{ ExecutionContext, Future }

case class DispatcherPipeData(
    consumerRecords: Vector[ConsumerRecord[String, String]],
    eventLog: Vector[EventLog]
) extends ProcessResult[String, String] {
  override val id: UUID = UUIDHelper.randomUUID

  def withEventLogs(newEventLogs: Vector[EventLog]): DispatcherPipeData = {
    copy(eventLog = eventLog)
  }

  def withConsumerRecords(newConsumerRecords: Vector[ConsumerRecord[String, String]]): DispatcherPipeData = {
    copy(consumerRecords = newConsumerRecords)
  }

}

object DispatcherPipeData {
  def empty: DispatcherPipeData = DispatcherPipeData(Vector.empty, Vector.empty)
}

@Singleton
class DefaultRecordsManager @Inject() (
    val reporter: Reporter,
    val executorFamily: ExecutorFamily,
    @Named(DefaultFailureCounter.name) failureCounter: Counter,
    config: Config
)(implicit ec: ExecutionContext)
  extends StringConsumerRecordsManager
  with LazyLogging {

  import reporter.Types._

  lazy val metricsSubNamespace: String = config.getString(ConsumerConfPaths.METRICS_SUB_NAMESPACE)

  override type A = DispatcherPipeData

  override val executor: Executor[Vector[ConsumerRecord[String, String]], Future[DispatcherPipeData]] = {
    executorFamily.dispatch
  }

  override val executorExceptionHandler: PartialFunction[Throwable, Future[DispatcherPipeData]] = {

    case e @ EmptyValueException(_, pipeData) =>
      logger.error(s"EmptyValueException: ${e.getMessage}", e)
      failureCounter.counter.labels(metricsSubNamespace).inc()
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = pipeData.consumerRecords.headOption.map(_.value().toString).getOrElse("No Value")))
      Future.successful(pipeData)
    case e @ ParsingIntoEventLogException(_, pipeData) =>
      logger.error(s"ParsingIntoEventLogException: ${e.getMessage}", e)
      failureCounter.counter.labels(metricsSubNamespace).inc()
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = pipeData.consumerRecords.headOption.map(_.value().toString).getOrElse("No Value")))
      Future.successful(pipeData)
    case e @ CreateProducerRecordException(_, pipeData) =>
      logger.error(s"CreateProducerRecordException: ${e.getMessage}", e)
      failureCounter.counter.labels(metricsSubNamespace).inc()
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = pipeData.consumerRecords.headOption.map(_.value().toString).getOrElse("No Value")))
      Future.successful(pipeData)
    case e @ CommitException(_, pipeData) =>
      logger.error(s"CommitException: ${e.getMessage}", e)
      failureCounter.counter.labels(metricsSubNamespace).inc()
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = pipeData.consumerRecords.headOption.map(_.value().toString).getOrElse("No Value")))
      Future.successful(pipeData)
  }

}
