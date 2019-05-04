package com.ubirch.lookup.process

import com.datastax.driver.core.exceptions.InvalidQueryException
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ProducerConfPaths
import com.ubirch.kafka.producer.StringProducer
import com.ubirch.lookup.ServiceTraits
import com.ubirch.lookup.models.{ LookupResult, Payload, QueryType, Signature }
import com.ubirch.lookup.services.kafka.consumer.LookupPipeData
import com.ubirch.lookup.util.Exceptions._
import com.ubirch.lookup.util.LookupJsonSupport
import com.ubirch.models.EventsDAO
import com.ubirch.process.Executor
import com.ubirch.util.Implicits.enrichedConfig
import com.ubirch.util._
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.{ ProducerRecord, RecordMetadata }
import org.json4s.JValue

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }

class LookupExecutor @Inject() (eventsDAO: EventsDAO)(implicit ec: ExecutionContext)
  extends Executor[Vector[ConsumerRecord[String, String]], Future[LookupPipeData]]
  with LazyLogging {

  override def apply(v1: Vector[ConsumerRecord[String, String]]): Future[LookupPipeData] = {

    val maybeConsumerRecord = v1.headOption
    val maybeKey = maybeConsumerRecord.map(_.key())
    val maybeValue = maybeConsumerRecord.map(_.value())
    val maybeQueryType = maybeConsumerRecord
      .flatMap(_.headers().headers(QueryType.QUERY_TYPE_HEADER).asScala.headOption)
      .map(_.value())
      .map(org.bouncycastle.util.Strings.fromUTF8ByteArray)
      .filter(QueryType.isValid)
      .flatMap(QueryType.fromString)

    val maybeFutureRes = for {
      key <- maybeKey
      value <- maybeValue
      queryType <- maybeQueryType
    } yield {
      if (value.isEmpty || key.isEmpty) {
        Future.successful(LookupPipeData(v1, Some(key), Some(queryType), Some(LookupResult.NotFound(key, queryType)), None, None))
      } else {

        val futureRes: Future[Option[JValue]] = queryType match {
          case Payload => eventsDAO.events.byIdAndCat(value, ServiceTraits.ADAPTER_CATEGORY).map(_.headOption)
          case Signature => eventsDAO.byValueAndNameAndCategory(value, Signature.value, ServiceTraits.ADAPTER_CATEGORY)
        }

        futureRes.map {
          case Some(ev) => LookupPipeData(v1, Some(key), Some(queryType), Some(LookupResult.Found(key, queryType, ev)), None, None)
          case None => LookupPipeData(v1, Some(key), Some(queryType), Some(LookupResult.NotFound(key, queryType)), None, None)
        }.recover {
          case e: InvalidQueryException =>
            logger.error("Error querying db: " + e)
            throw e
          case e: Exception =>
            logger.error("Error querying data: " + e)
            throw LookupExecutorException(
              "Error storing data",
              LookupPipeData(v1, Some(key), Some(queryType), Some(LookupResult.NotFound(key, queryType)), None, None), e.getMessage
            )
        }
      }

    }

    maybeFutureRes
      .getOrElse(throw LookupExecutorException("No key or value were found", LookupPipeData(v1, maybeKey, maybeQueryType, None, None, None), ""))

  }

}

/**
  * Represents an executor that creates the producer record object that will be eventually published to Kafka
  *
  * @param config Represents a config object to read config values from
  * @param ec     Represents an execution context
  */
class CreateProducerRecord @Inject() (config: Config)(implicit ec: ExecutionContext)
  extends Executor[Future[LookupPipeData], Future[LookupPipeData]]
  with ProducerConfPaths {
  override def apply(v1: Future[LookupPipeData]): Future[LookupPipeData] = {

    v1.map { v1 =>

      try {

        val topic = config.getStringAsOption(TOPIC_PATH).getOrElse("com.ubirch.eventlog")

        val output = v1.lookupResult
          .flatMap(x => x.event.map(y => (x, y)))
          .map { case (x, y) => (x, LookupJsonSupport.FromJson[LookupResult](y)) }
          .map { case (x, y) =>
            val commitDecision: Decision[ProducerRecord[String, String]] = {
              Go(ProducerRecordHelper.toRecord(topic, x.key, y.toString, Map(QueryType.QUERY_TYPE_HEADER -> x.queryType.value)))
            }

            commitDecision

          }
          .map(x => v1.copy(producerRecord = Some(x)))
          .getOrElse(throw CreateProducerRecordException("Empty Materials", v1))

        output

      } catch {

        case e: Exception =>
          throw CreateProducerRecordException(e.getMessage, v1)

      }
    }

  }
}

/**
  * Represents an executor that commits a producer record
  *
  * @param stringProducer Represents a producer.
  * @param config         Represents a config object to read config values from
  * @param ec             Represents an execution context
  */
class Commit @Inject() (stringProducer: StringProducer, config: Config)(implicit ec: ExecutionContext) extends Executor[Future[LookupPipeData], Future[LookupPipeData]] {

  val futureHelper = new FutureHelper()

  def commit(value: Decision[ProducerRecord[String, String]]): Future[Option[RecordMetadata]] = {
    value match {
      case Go(record) =>
        val javaFuture = stringProducer.getProducerOrCreate.send(record)
        futureHelper.fromJavaFuture(javaFuture).map(x => Option(x))
      case Ignore() =>
        Future.successful(None)
    }
  }

  override def apply(v1: Future[LookupPipeData]): Future[LookupPipeData] = {

    v1.flatMap { v1 =>

      try {

        v1.producerRecord
          .map(x => commit(x))
          .map(x => x.map(y => v1.copy(recordMetadata = y)))
          .getOrElse(throw CommitException("No Producer Record Found", v1))

      } catch {

        case e: Exception =>
          Future.failed(CommitException(e.getMessage, v1))

      }
    }

  }
}

/**
  * Represents a description of a family of executors that can be composed.
  */
trait ExecutorFamily {

  def lookupExecutor: LookupExecutor

  def createProducerRecord: CreateProducerRecord

  def commit: Commit

}

@Singleton
class DefaultExecutorFamily @Inject() (
    val lookupExecutor: LookupExecutor,
    val createProducerRecord: CreateProducerRecord,
    val commit: Commit
) extends ExecutorFamily
