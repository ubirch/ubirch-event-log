package com.ubirch.chainer.services.kafka.consumer

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.chainer.models.Chainer
import com.ubirch.chainer.process.ExecutorFamily
import com.ubirch.chainer.util._
import com.ubirch.models.{ Error, EventLog }
import com.ubirch.process.Executor
import com.ubirch.services.kafka.consumer.{ EventLogsPipeData, StringConsumerRecordsManager }
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.util.Decision
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.{ ProducerRecord, RecordMetadata }

import scala.concurrent.{ ExecutionContext, Future }

case class ChainerPipeData(consumerRecords: Vector[ConsumerRecord[String, String]], eventLogs: Vector[EventLog], chainer: Option[Chainer[EventLog]], treeEventLog: Option[EventLog], producerRecords: Vector[Decision[ProducerRecord[String, String]]], recordsMetadata: Vector[RecordMetadata])
  extends EventLogsPipeData[String]

@Singleton
class DefaultChainerManager @Inject() (val reporter: Reporter, executorFamily: ExecutorFamily)(implicit ec: ExecutionContext)
  extends StringConsumerRecordsManager
  with LazyLogging {

  import reporter.Types._

  type A = ChainerPipeData

  def executor: Executor[Vector[ConsumerRecord[String, String]], Future[ChainerPipeData]] = {
    executorFamily.filterEmpty andThen
      executorFamily.eventLogParser andThen
      executorFamily.eventLogSigner andThen
      executorFamily.treeCreatorExecutor andThen
      executorFamily.treeEventLogCreation andThen
      executorFamily.createTreeProducerRecord andThen
      executorFamily.commit
  }

  def executorExceptionHandler: PartialFunction[Throwable, Future[ChainerPipeData]] = {
    case e: EmptyValueException =>
      logger.error("EmptyValueException: " + e.getMessage)
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name))
      Future.successful(e.pipeData)
    case e: ParsingIntoEventLogException =>
      logger.error("ParsingIntoEventLogException: " + e.getMessage)
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = e.pipeData.consumerRecords.headOption.map(_.value()).getOrElse("No value")))
      Future.successful(e.pipeData)
    case e: SigningEventLogException =>
      logger.error("SigningEventLogException: " + e.getMessage)
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = e.pipeData.consumerRecords.headOption.map(_.value()).getOrElse("No value")))
      Future.successful(e.pipeData)
    case e @ TreeEventLogCreationException(_, pipeData) =>
      logger.error("TreeEventLogCreationException: " + e.getMessage)
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = e.pipeData.consumerRecords.headOption.map(_.value()).getOrElse("No value")))
      Future.successful(pipeData)
    case e @ CreateTreeProducerRecordException(_, pipeData) =>
      logger.error("CreateProducerRecordException: " + e.getMessage)
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = e.pipeData.consumerRecords.headOption.map(_.value()).getOrElse("No value")))
      Future.successful(pipeData)
    case e @ CommitException(_, pipeData) =>
      //TODO: should we just retry the whole loop?
      logger.error("CommitException: " + e.getMessage)
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = e.pipeData.consumerRecords.headOption.map(_.value()).getOrElse("No value")))
      Future.successful(pipeData)
  }

}
