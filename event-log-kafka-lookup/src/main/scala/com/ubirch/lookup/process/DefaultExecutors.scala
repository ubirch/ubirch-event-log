package com.ubirch.lookup.process

import com.datastax.driver.core.exceptions.InvalidQueryException
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ProducerConfPaths
import com.ubirch.kafka.producer.StringProducer
import com.ubirch.lookup.models.{ AnchorsNoPath, AnchorsWithPath, Finder, LookupResult, Params, QueryDepth, QueryType, ResponseForm, ShortestPath, Simple, UpperLower, VertexStruct }
import com.ubirch.lookup.services.kafka.consumer.LookupPipeData
import com.ubirch.lookup.util.Exceptions._
import com.ubirch.lookup.util.LookupJsonSupport
import com.ubirch.models.{ JValueGenericResponse, Values }
import com.ubirch.process.Executor
import com.ubirch.util._
import javax.inject._
import monix.execution.FutureUtils
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.json4s.JsonAST.JNull

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
import concurrent.duration._
import scala.language.postfixOps

@Singleton
class LookupExecutor @Inject() (finder: Finder)(implicit ec: ExecutionContext)
  extends Executor[Vector[ConsumerRecord[String, String]], Future[LookupPipeData]]
  with LazyLogging {

  implicit val scheduler = monix.execution.Scheduler(ec)

  def simple(key: String, value: String, queryType: QueryType)(v1: Vector[ConsumerRecord[String, String]]): Future[LookupPipeData] =
    finder.findUPP(value, queryType).map {
      case Some(ev) =>
        LookupPipeData(v1, Some(key), Some(queryType), Some(LookupResult.Found(key, queryType, ev.event, JNull)), None, None)
      case None =>
        LookupPipeData(v1, Some(key), Some(queryType), Some(LookupResult.NotFound(key, queryType)), None, None)
    }

  def shortestPath(key: String, value: String, queryType: QueryType, responseForm: ResponseForm)(v1: Vector[ConsumerRecord[String, String]]): Future[LookupPipeData] = {

    finder.findUPPWithShortestPath(value, queryType).map {
      case (Some(ev), path, maybeAnchors) =>
        val anchors = responseForm match {
          case AnchorsNoPath => LookupExecutor.shortestPathAsJValue(maybeAnchors)
          case AnchorsWithPath => LookupExecutor.shortestPathAsJValue(path, maybeAnchors)

        }
        LookupPipeData(v1, Some(key), Some(queryType), Some(LookupResult.Found(key, queryType, ev.event, anchors)), None, None)

      case (None, _, _) =>
        LookupPipeData(v1, Some(key), Some(queryType), Some(LookupResult.NotFound(key, queryType)), None, None)
    }
  }

  def upperLower(key: String, value: String, queryType: QueryType, responseForm: ResponseForm)(v1: Vector[ConsumerRecord[String, String]]): Future[LookupPipeData] =
    finder.findUPPWithUpperLowerBounds(value, queryType).map {
      case (Some(ev), upperPath, upperBlocks, lowerPath, lowerBlocks) =>
        val anchors = responseForm match {
          case AnchorsNoPath => LookupExecutor.upperAndLowerAsJValue(upperBlocks, lowerBlocks)
          case AnchorsWithPath => LookupExecutor.upperAndLowerAsJValue(upperPath, upperBlocks, lowerPath, lowerBlocks)

        }
        LookupPipeData(v1, Some(key), Some(queryType), Some(LookupResult.Found(key, queryType, ev.event, anchors)), None, None)
      case (None, _, _, _, _) =>
        LookupPipeData(v1, Some(key), Some(queryType), Some(LookupResult.NotFound(key, queryType)), None, None)
    }

  override def apply(v1: Vector[ConsumerRecord[String, String]]): Future[LookupPipeData] = {

    val maybeConsumerRecord = v1.headOption
    val maybeKey = maybeConsumerRecord.map(_.key())
    val maybeValue = maybeConsumerRecord.map(_.value())

    val maybeQueryType: Option[QueryType] = Params.get(maybeConsumerRecord, QueryType)
    val queryDepth: QueryDepth = Params.getOrElse(maybeConsumerRecord, QueryDepth, Simple)
    val responseForm: ResponseForm = Params.getOrElse(maybeConsumerRecord, ResponseForm, AnchorsNoPath)

    val maybeFutureRes = for {
      key <- maybeKey
      value <- maybeValue
      queryType <- maybeQueryType
    } yield {

      if (value.isEmpty || key.isEmpty)
        Future.successful(LookupPipeData(v1, Some(key), Some(queryType), Some(LookupResult.NotFound(key, queryType)), None, None))
      else {

        val res = queryDepth match {
          case Simple => simple(key, value, queryType)(v1)
          case ShortestPath => shortestPath(key, value, queryType, responseForm)(v1)
          case UpperLower => upperLower(key, value, queryType, responseForm)(v1)
        }

        FutureUtils
          .timeout(res, 20 seconds)
          .recover {
            case e: InvalidQueryException =>
              logger.error(s"Error querying db ($queryDepth): ", e)
              throw e
            case e: scala.concurrent.TimeoutException =>
              logger.error(s"Timeout querying data ($queryDepth): ", e)
              val res = LookupResult.Error(key, queryType, s"Error processing request ($queryDepth): " + e.getMessage)
              LookupPipeData(v1, Some(key), Some(queryType), Option(res), None, None)
            case e: Exception =>
              logger.error(s"Error querying data ($queryDepth): ", e)
              val res = LookupResult.Error(key, queryType, s"Error processing request ($queryDepth): " + e.getMessage)
              throw LookupExecutorException(
                s"Error querying data ($queryDepth)",
                LookupPipeData(v1, Some(key), Some(queryType), Some(res), None, None),
                e.getMessage
              )
          }

      }

    }

    maybeFutureRes
      .getOrElse(throw LookupExecutorException("No key or value was found", LookupPipeData(v1, maybeKey, maybeQueryType, None, None, None), ""))

  }

}

object LookupExecutor {

  def shortestPathAsJValue(maybeAnchors: Seq[VertexStruct]) =
    LookupJsonSupport.ToJson[Seq[VertexStruct]](maybeAnchors).get

  def shortestPathAsJValue(path: Seq[VertexStruct], maybeAnchors: Seq[VertexStruct]) = {
    val anchors = Map(Values.SHORTEST_PATH -> path, Values.BLOCKCHAINS -> maybeAnchors)
    LookupJsonSupport.ToJson(anchors).get
  }

  def upperAndLowerAsJValue(upperPath: Seq[VertexStruct], upperBlocks: Seq[VertexStruct], lowerPath: Seq[VertexStruct], lowerBlocks: Seq[VertexStruct]) = {
    val anchors = Map(
      Values.UPPER_PATH -> upperPath,
      Values.UPPER_BLOCKCHAINS -> upperBlocks,
      Values.LOWER_PATH -> lowerPath,
      Values.LOWER_BLOCKCHAINS -> lowerBlocks
    )
    LookupJsonSupport.ToJson(anchors).get
  }

  def upperAndLowerAsJValue(upperBlocks: Seq[VertexStruct], lowerBlocks: Seq[VertexStruct]) = {
    val anchors = Map(
      Values.UPPER_BLOCKCHAINS -> upperBlocks,
      Values.LOWER_BLOCKCHAINS -> lowerBlocks
    )
    LookupJsonSupport.ToJson(anchors).get
  }

}

/**
  * Represents an executor that creates the producer record object that will be eventually published to Kafka
  *
  * @param config Represents a config object to read config values from
  * @param ec     Represents an execution context
  */
@Singleton
class CreateProducerRecord @Inject() (config: Config)(implicit ec: ExecutionContext)
  extends Executor[Future[LookupPipeData], Future[LookupPipeData]]
  with ProducerConfPaths {
  override def apply(v1: Future[LookupPipeData]): Future[LookupPipeData] = {

    v1.map { v1 =>

      try {

        val topic = config.getString(TOPIC_PATH)

        val output = v1.lookupResult
          .map { x =>
            val lookupJValue = LookupJsonSupport.ToJson[LookupResult](x).get
            val gr = JValueGenericResponse(success = x.success, x.message, lookupJValue)

            (x, LookupJsonSupport.ToJson[JValueGenericResponse](gr))
          }
          .map { case (x, y) =>
            val commitDecision: ProducerRecord[String, String] = {
              ProducerRecordHelper.toRecord(topic, x.key, y.toString, Map(QueryType.HEADER -> x.queryType))
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

@Singleton
class Commit @Inject() (stringProducer: StringProducer)(implicit ec: ExecutionContext)
  extends Executor[Future[LookupPipeData], Future[LookupPipeData]] {

  override def apply(v1: Future[LookupPipeData]): Future[LookupPipeData] = {

    v1.flatMap { v1 =>

      try {

        v1.producerRecord
          .map(x => stringProducer.send(x))
          .map(x => x.map(y => v1.copy(recordMetadata = Some(y))))
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
