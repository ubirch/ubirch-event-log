package com.ubirch.verification.services.eventlog

import java.util.UUID

import com.datastax.driver.core.exceptions.InvalidQueryException
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.kafka.consumer.ProcessResult
import com.ubirch.models.Values
import com.ubirch.util.{ JValueGenericResponse, ProducerRecordHelper, UUIDHelper }
import com.ubirch.verification.models._
import com.ubirch.verification.services.Finder
import com.ubirch.verification.util.Exceptions.{ CreateProducerRecordException, LookupExecutorException }
import com.ubirch.verification.util.LookupJsonSupport
import com.ubirch.verification.util.LookupJsonSupport.formats
import javax.inject.Inject
import monix.execution.{ FutureUtils, Scheduler }
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.{ ProducerRecord, RecordMetadata }
import org.json4s.JValue
import org.json4s.JsonAST.JNull

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future, TimeoutException }
import scala.language.postfixOps

class NewEventLogClient @Inject() (finder: Finder)(implicit ec: ExecutionContext) extends EventLogClient with LazyLogging {

  implicit val scheduler: Scheduler = monix.execution.Scheduler(ec)

  def decorateBlockchain(blockchainInfo: BlockchainInfo, vertices: Seq[VertexStruct]): Future[Seq[VertexStruct]] = {

    def extended = {
      val res = vertices
        .map(x => (x, x.getBoth(Values.HASH, Values.PUBLIC_CHAIN_CATEGORY.toLowerCase())))
        .map {
          case (vertex, Some((hash, category))) if hash.isInstanceOf[String] && category.isInstanceOf[String] =>
            logger.debug(s"blockchain_info=${blockchainInfo.value} hash=$hash category=$category")
            finder
              .findEventLog(hash.toString, category.toString)
              .map(_.map(_.event))
              .map { x =>

                val blockchainInfo = x.map {
                  _.foldField(Map.empty[String, String]) { (acc, curr) =>
                    acc ++ Map(curr._1 -> curr._2.extractOpt[String].getOrElse("Nothing Extracted"))
                  }
                }

                blockchainInfo
                  .map(bi => vertex.addProperties(bi))
                  .orElse {
                    logger.debug("Defaulting to origin")
                    Option(vertex)
                  }

              }
          case other =>
            logger.debug("Nothing to decorate: " + other.toString())
            Future.successful(None)
        }

      Future
        .sequence(res)
        .map(_.flatMap(_.toList))
    }

    blockchainInfo match {
      case Normal => Future.successful(vertices)
      case Extended => extended
    }

  }

  def simple(key: String, value: String, queryType: QueryType): Future[LookupPipeDataNew] =
    finder.findUPP(value, queryType).map {
      case Some(ev) =>
        LookupPipeDataNew(Some(value), Some(key), Some(queryType), Some(LookupResult.Found(key, queryType, ev.event, JNull)), None, None)

      case None =>
        LookupPipeDataNew(Some(value), Some(key), Some(queryType), Some(LookupResult.NotFound(key, queryType)), None, None)
    }

  def shortestPath(key: String, value: String, queryType: QueryType, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupPipeDataNew] = {

    val res: Future[LookupPipeDataNew] = for {
      (maybeEventLog, path, maybeAnchors) <- finder.findUPPWithShortestPath(value, queryType)
      decoratedAnchors <- decorateBlockchain(blockchainInfo, maybeAnchors)
    } yield {
      val anchors = responseForm match {
        case AnchorsNoPath => LookupExecutor.shortestPathAsJValue(decoratedAnchors)
        case AnchorsWithPath => LookupExecutor.shortestPathAsJValue(path, decoratedAnchors)
      }

      //This is set here, so we can eliminate a false positive response for when there is no upp record BUT the result is not an error
      val result = maybeEventLog.map { ev =>
        LookupResult.Found(key, queryType, ev.event, anchors)
      }.getOrElse {
        LookupResult.NotFound(key, queryType)
      }

      LookupPipeDataNew(Some(value), Some(key), Some(queryType), Some(result), None, None)
    }

    res.recover {
      case e: Exception =>
        logger.error("For Comprehension Res (shortestPath): " + e.getMessage)
        logger.error("For Comprehension Res (shortestPath): ", e)
        LookupPipeDataNew(Some(value), Some(key), Some(queryType), Some(LookupResult.NotFound(key, queryType)), None, None)
    }

  }

  def upperLower(key: String, value: String, queryType: QueryType, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupPipeDataNew] = {

    val res = for {
      (maybeEventLog, upperPath, upperBlocks, lowerPath, lowerBlocks) <- finder.findUPPWithUpperLowerBounds(value, queryType)
      decoratedUpperAnchors <- decorateBlockchain(blockchainInfo, upperBlocks)
      decoratedLowerAnchors <- decorateBlockchain(blockchainInfo, lowerBlocks)
    } yield {

      val anchors = responseForm match {
        case AnchorsNoPath => LookupExecutor.upperAndLowerAsJValue(decoratedUpperAnchors, decoratedLowerAnchors)
        case AnchorsWithPath => LookupExecutor.upperAndLowerAsJValue(upperPath, decoratedUpperAnchors, lowerPath, decoratedLowerAnchors)

      }

      //This is set here, so we can eliminate a false positive response for when there is no upp record BUT the result is not an error
      val result = maybeEventLog.map { ev =>
        LookupResult.Found(key, queryType, ev.event, anchors)
      }.getOrElse {
        LookupResult.NotFound(key, queryType)
      }

      LookupPipeDataNew(Some(value), Some(key), Some(queryType), Some(result), None, None)

    }

    res.recover {
      case e: Exception =>
        logger.error("For Comprehension Res (upperLower):  " + e.getMessage)
        logger.error("For Comprehension Res (upperLower):  ", e)
        LookupPipeDataNew(Some(value), Some(key), Some(queryType), Some(LookupResult.NotFound(key, queryType)), None, None)

    }

  }

  override def getEventByHash(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm,
      blockchainInfo: BlockchainInfo = Normal): Future[EventLogClient.Response] =

    getEvent(hash, queryDepth, responseForm, blockchainInfo, Some(Payload))

  override def getEventBySignature(signature: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm,
      blockchainInfo: BlockchainInfo): Future[EventLogClient.Response] =

    getEvent(signature, queryDepth, responseForm, blockchainInfo, Some(Signature))

  private def getEvent(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo, maybeQueryType: Option[QueryType]) = {
    //Todo: remove key -> or rather define in API
    val key = UUID.randomUUID().toString
    val value: String = new String(hash) //hash.map(_.toChar).mkString
    //Todo: Check if this might be ok

    val maybeFutureRes = for {
      queryType <- maybeQueryType
    } yield {

      if (value.isEmpty || key.isEmpty)
        //Todo: Api should not accept queries without hash
        Future.successful(LookupPipeDataNew(Some(value), Some(key), Some(queryType), Some(LookupResult.NotFound(key, queryType)), None, None))
      else {

        lazy val res: Future[LookupPipeDataNew] = queryDepth match {
          case Simple => simple(key, value, queryType)
          case ShortestPath => shortestPath(key, value, queryType, responseForm, blockchainInfo)
          case UpperLower => upperLower(key, value, queryType, responseForm, blockchainInfo)
        }

        FutureUtils
          .timeout(res, 20 seconds)
          .recover {
            case e: InvalidQueryException =>
              logger.error(s"Error querying db ($queryDepth): ", e)
              throw e
            case e: TimeoutException =>
              logger.error(s"Timeout querying data ($queryDepth): ", e)
              val res = LookupResult.Error(key, queryType, s"Error processing request ($queryDepth): " + e.getMessage)
              LookupPipeDataNew(Some(value), Some(key), Some(queryType), Option(res), None, None)
            case e: Exception =>
              logger.error(s"Error querying data ($queryDepth): ", e)
              val res = LookupResult.Error(key, queryType, s"Error processing request ($queryDepth): " + e.getMessage)
              throw LookupExecutorException(
                s"Error querying data ($queryDepth)",
                LookupPipeDataNew(Some(value), Some(key), Some(queryType), Some(res), None, None),
                e.getMessage
              )
          }

      }

    }

    val result: Future[LookupPipeDataNew] = maybeFutureRes.getOrElse(throw LookupExecutorException("No key or value was found", LookupPipeDataNew(Some(value), Some(key), maybeQueryType, None, None, None), ""))
    result.map(createResponseFromLookupPipeDataNew)
  }

  def createResponseFromLookupPipeDataNew(lookupPipeDataNew: LookupPipeDataNew): EventLogClient.Response = {
    val topic = "irrelevant topic ;)"

    val output: LookupPipeDataNew =
      lookupPipeDataNew
        .lookupResult
        .map { x: LookupResult =>
          val lookupJValue = LookupJsonSupport.ToJson[LookupResult](x).get
          val gr = JValueGenericResponse(success = x.success, x.message, lookupJValue)
          (x, LookupJsonSupport.ToJson[JValueGenericResponse](gr))
        }
        .map {
          case (x: LookupResult, y) =>
            ProducerRecordHelper.toRecord(topic, x.key, y.toString, Map(QueryType.HEADER -> x.queryType))
        }
        .map(producerRec => lookupPipeDataNew.copy(producerRecord = Some(producerRec)))
        .getOrElse(throw CreateProducerRecordException("Empty Materials", lookupPipeDataNew))

    output.producerRecord
      .map(producerRec => EventLogClient.Response.fromJson(producerRec.value())).get

  }

}

object LookupExecutor {

  def shortestPathAsJValue(maybeAnchors: Seq[VertexStruct]): JValue =
    LookupJsonSupport.ToJson[Seq[VertexStruct]](maybeAnchors).get

  def shortestPathAsJValue(path: Seq[VertexStruct], maybeAnchors: Seq[VertexStruct]): JValue = {
    val anchors = Map(Values.SHORTEST_PATH -> path.map(v => v.toDumbVertexStruct), Values.BLOCKCHAINS -> maybeAnchors.map(v => v.toDumbVertexStruct))
    LookupJsonSupport.ToJson(anchors).get
  }

  def upperAndLowerAsJValue(upperPath: Seq[VertexStruct], upperBlocks: Seq[VertexStruct], lowerPath: Seq[VertexStruct], lowerBlocks: Seq[VertexStruct]): JValue = {
    val anchors = Map(
      Values.UPPER_PATH -> upperPath,
      Values.UPPER_BLOCKCHAINS -> upperBlocks,
      Values.LOWER_PATH -> lowerPath,
      Values.LOWER_BLOCKCHAINS -> lowerBlocks
    )
    LookupJsonSupport.ToJson(anchors).get
  }

  def upperAndLowerAsJValue(upperBlocks: Seq[VertexStruct], lowerBlocks: Seq[VertexStruct]): JValue = {
    val anchors = Map(
      Values.UPPER_BLOCKCHAINS -> upperBlocks,
      Values.LOWER_BLOCKCHAINS -> lowerBlocks
    )
    LookupJsonSupport.ToJson(anchors).get
  }

}

case class LookupPipeDataNew(
    value: Option[String] = None,
    key: Option[String],
    queryType: Option[QueryType],
    lookupResult: Option[LookupResult],
    producerRecord: Option[ProducerRecord[String, String]],
    recordMetadata: Option[RecordMetadata],
    consumerRecords: Vector[ConsumerRecord[String, String]] = Vector.empty
) extends ProcessResult[String, String] {
  val id: UUID = UUIDHelper.randomUUID
}