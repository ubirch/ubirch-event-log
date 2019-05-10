package com.ubirch.lookup.services.kafka.consumer

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.kafka.consumer._
import com.ubirch.lookup.models.{ LookupResult, QueryType }
import com.ubirch.lookup.process.ExecutorFamily
import com.ubirch.lookup.util.Exceptions.{ CommitException, CreateProducerRecordException, LookupExecutorException }
import com.ubirch.models.Error
import com.ubirch.process.Executor
import com.ubirch.services.kafka.consumer.StringConsumerRecordsManager
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.util.{ Decision, UUIDHelper }
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.{ ProducerRecord, RecordMetadata }

import scala.concurrent.{ ExecutionContext, Future }

case class LookupPipeData(
    consumerRecords: Vector[ConsumerRecord[String, String]],
    key: Option[String],
    queryType: Option[QueryType],
    lookupResult: Option[LookupResult],
    producerRecord: Option[Decision[ProducerRecord[String, String]]],
    recordMetadata: Option[RecordMetadata]
) extends ProcessResult[String, String] {
  override val id: UUID = UUIDHelper.randomUUID
}

@Singleton
class DefaultRecordsManager @Inject() (val reporter: Reporter, val executorFamily: ExecutorFamily)(implicit ec: ExecutionContext)
  extends StringConsumerRecordsManager
  with LazyLogging {

  import reporter.Types._

  override type A = LookupPipeData

  override def executor: Executor[Vector[ConsumerRecord[String, String]], Future[LookupPipeData]] = {

    executorFamily.lookupExecutor andThen
      executorFamily.createProducerRecord andThen
      executorFamily.commit

  }

  def executorExceptionHandlerPF: PartialFunction[Throwable, Future[LookupPipeData]] = {

    case e @ LookupExecutorException(_, pipeData, _) =>
      logger.error("LookupExecutorException: " + e.getMessage)
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = pipeData.consumerRecords.headOption.map(_.value().toString).getOrElse("No Value")))
      Future.successful(pipeData)
    case e @ CreateProducerRecordException(_, pipeData) =>
      logger.error("CreateProducerRecordException: " + e.getMessage)
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = pipeData.consumerRecords.headOption.map(_.value().toString).getOrElse("No Value")))
      Future.successful(pipeData)
    case e @ CommitException(_, pipeData) =>
      logger.error("CommitException: " + e.getMessage)
      reporter.report(Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = pipeData.consumerRecords.headOption.map(_.value().toString).getOrElse("No Value")))
      Future.successful(pipeData)

  }

  override def executorExceptionHandler: PartialFunction[Throwable, Future[LookupPipeData]] = {
    val exec = executorFamily.createProducerRecord andThen executorFamily.commit
    executorExceptionHandlerPF.andThen { f =>
      exec(f)
    }
  }

}
