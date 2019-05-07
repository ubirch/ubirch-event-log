package com.ubirch.dispatcher.services.kafka.consumer

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.dispatcher.process.ExecutorFamily
import com.ubirch.dispatcher.util.Exceptions.{ CommitException, CreateProducerRecordException, EmptyValueException, ParsingIntoEventLogException }
import com.ubirch.models.{ Error, EventLog }
import com.ubirch.process.Executor
import com.ubirch.services.kafka.consumer.{ EventLogPipeData, StringConsumerRecordsManager }
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.util.Decision
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.{ ProducerRecord, RecordMetadata }

import scala.concurrent.{ ExecutionContext, Future }

case class DispatcherPipeData(
    consumerRecords: Vector[ConsumerRecord[String, String]],
    eventLog: Option[EventLog],
    producerRecords: Vector[Decision[ProducerRecord[String, String]]],
    recordsMetadata: Vector[RecordMetadata]
) extends EventLogPipeData[String]

@Singleton
class DefaultRecordsManager @Inject() (val reporter: Reporter, val executorFamily: ExecutorFamily)(implicit ec: ExecutionContext)
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
      logger.debug("EmptyValueException: " + e.getMessage)
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = pipeData.consumerRecords.headOption.map(_.value().toString).getOrElse("No Value")))
      Future.successful(pipeData)
    case e @ ParsingIntoEventLogException(_, pipeData) =>
      logger.debug("ParsingIntoEventLogException: " + e.getMessage)
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = pipeData.consumerRecords.headOption.map(_.value().toString).getOrElse("No Value")))
      Future.successful(pipeData)
    case e @ CreateProducerRecordException(_, pipeData) =>
      logger.debug("CreateProducerRecordException: " + e.getMessage)
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = pipeData.consumerRecords.headOption.map(_.value().toString).getOrElse("No Value")))
      Future.successful(pipeData)
    case e @ CommitException(_, pipeData) =>
      logger.debug("CommitException: " + e.getMessage)
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = pipeData.consumerRecords.headOption.map(_.value().toString).getOrElse("No Value")))
      Future.successful(pipeData)

  }

}
