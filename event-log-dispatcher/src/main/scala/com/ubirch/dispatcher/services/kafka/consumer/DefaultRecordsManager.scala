package com.ubirch.dispatcher.services.kafka.consumer

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.dispatcher.process.ExecutorFamily
import com.ubirch.dispatcher.util.Exceptions.{ CommitException, CreateProducerRecordException, EmptyValueException, ParsingIntoEventLogException }
import com.ubirch.kafka.consumer.ProcessResult
import com.ubirch.models.{ Error, EventLog }
import com.ubirch.process.Executor
import com.ubirch.services.kafka.consumer.StringConsumerRecordsManager
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.services.metrics.{ Counter, DefaultConsumerRecordsManagerCounter }
import com.ubirch.util.{ Decision, UUIDHelper }
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.{ ProducerRecord, RecordMetadata }

import scala.concurrent.{ ExecutionContext, Future }

case class DispatcherPipeData(
    consumerRecords: Vector[ConsumerRecord[String, String]],
    eventLog: Vector[EventLog],
    producerRecords: Vector[Decision[ProducerRecord[String, String]]],
    recordsMetadata: Vector[RecordMetadata]
) extends ProcessResult[String, String] {
  override val id: UUID = UUIDHelper.randomUUID

  def withConsumerRecords(newConsumerRecords: Vector[ConsumerRecord[String, String]]): DispatcherPipeData = {
    copy(consumerRecords = newConsumerRecords)
  }

}

object DispatcherPipeData {
  def empty: DispatcherPipeData = DispatcherPipeData(Vector.empty, Vector.empty, Vector.empty, Vector.empty)
}

@Singleton
class DefaultRecordsManager @Inject() (
    val reporter: Reporter,
    val executorFamily: ExecutorFamily,
    @Named(DefaultConsumerRecordsManagerCounter.name) counter: Counter
)(implicit ec: ExecutionContext)
  extends StringConsumerRecordsManager
  with LazyLogging {

  import reporter.Types._

  override type A = DispatcherPipeData

  override def executor: Executor[Vector[ConsumerRecord[String, String]], Future[DispatcherPipeData]] = {

    executorFamily.filterEmpty andThen
      executorFamily.eventLogParser andThen
      executorFamily.createProducerRecords andThen
      executorFamily.commit

  }

  override def executorExceptionHandler: PartialFunction[Throwable, Future[DispatcherPipeData]] = {

    case e @ EmptyValueException(_, pipeData) =>
      logger.error(s"EmptyValueException: ${e.getMessage}", e)
      counter.counter.labels("EmptyValueException").inc()
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = pipeData.consumerRecords.headOption.map(_.value().toString).getOrElse("No Value")))
      Future.successful(pipeData)
    case e @ ParsingIntoEventLogException(_, pipeData) =>
      logger.error(s"ParsingIntoEventLogException: ${e.getMessage}", e)
      counter.counter.labels("ParsingIntoEventLogException").inc()
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = pipeData.consumerRecords.headOption.map(_.value().toString).getOrElse("No Value")))
      Future.successful(pipeData)
    case e @ CreateProducerRecordException(_, pipeData) =>
      logger.error(s"CreateProducerRecordException: ${e.getMessage}", e)
      counter.counter.labels("CreateProducerRecordException").inc()
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = pipeData.consumerRecords.headOption.map(_.value().toString).getOrElse("No Value")))
      Future.successful(pipeData)
    case e @ CommitException(_, pipeData) =>
      logger.error(s"CommitException: ${e.getMessage}", e)
      counter.counter.labels("CommitException").inc()
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = pipeData.consumerRecords.headOption.map(_.value().toString).getOrElse("No Value")))
      Future.successful(pipeData)
  }

}
