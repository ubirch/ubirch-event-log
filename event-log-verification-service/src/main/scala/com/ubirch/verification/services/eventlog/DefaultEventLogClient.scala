package com.ubirch.verification.services.eventlog

import com.datastax.driver.core.exceptions.InvalidQueryException
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.Values
import com.ubirch.verification.models._
import com.ubirch.verification.services.Finder
import com.ubirch.verification.util.Exceptions.LookupExecutorException
import com.ubirch.verification.util.LookupJsonSupport.formats
import com.ubirch.verification.util.{ HashHelper, LookupJsonSupport }
import javax.inject._
import monix.execution.{ FutureUtils, Scheduler }
import org.json4s.JValue
import org.json4s.JsonAST.JNull

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future, TimeoutException }
import scala.language.postfixOps

@Singleton
class DefaultEventLogClient @Inject() (finder: Finder)(implicit ec: ExecutionContext) extends EventLogClient with LazyLogging {

  implicit val scheduler: Scheduler = monix.execution.Scheduler(ec)

  override def getEventByHash(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo = Normal): Future[LookupResult] =
    getEvent(hash, queryDepth, responseForm, blockchainInfo, Some(Payload))

  override def getEventBySignature(signature: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupResult] =
    getEvent(signature, queryDepth, responseForm, blockchainInfo, Some(Signature))

  private def getEvent(hash: Array[Byte], queryDepth: QueryDepth, responseForm: ResponseForm, blockchainInfo: BlockchainInfo, maybeQueryType: Option[QueryType]): Future[LookupResult] = {

    val hashAsString: String = HashHelper.bytesToPrintableId(hash)

    val maybeFutureRes = for {
      queryType <- maybeQueryType
    } yield {

      if (hashAsString.isEmpty)
        //Todo: Api should not accept queries without hash
        Future.successful(LookupResult.NotFound(hashAsString, queryType))
      else {

        lazy val res: Future[LookupResult] = queryDepth match {
          case Simple => simple(hashAsString, queryType)
          case ShortestPath => shortestPath(hashAsString, queryType, responseForm, blockchainInfo)
          case UpperLower => upperLower(hashAsString, queryType, responseForm, blockchainInfo)
        }

        FutureUtils
          .timeout(res, 20 seconds)
          .recover {
            case e: InvalidQueryException =>
              logger.error(s"Error querying db ($queryDepth): ", e)
              throw e
            case e: TimeoutException =>
              logger.error(s"Timeout querying data ($queryDepth): ", e)
              LookupResult.Error(hashAsString, queryType, s"Error processing request ($queryDepth): " + e.getMessage)
            case e: Exception =>
              logger.error(s"Error querying data ($queryDepth): ", e)
              val res = LookupResult.Error(hashAsString, queryType, s"Error processing request ($queryDepth): " + e.getMessage)
              throw LookupExecutorException(s"Error querying data ($queryDepth)", Some(res), e.getMessage)
          }

      }

    }

    maybeFutureRes.getOrElse(throw LookupExecutorException("No hash was found", None, ""))

  }

  private def simple(value: String, queryType: QueryType): Future[LookupResult] =
    finder.findUPP(value, queryType).map {
      case Some(ev) => LookupResult.Found(value, queryType, ev.event, JNull)
      case None => LookupResult.NotFound(value, queryType)
    }

  private def shortestPath(value: String, queryType: QueryType, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupResult] = {

    val res: Future[LookupResult] = for {
      (maybeEventLog, path, maybeAnchors) <- finder.findUPPWithShortestPath(value, queryType)
      decoratedAnchors <- decorateBlockchain(blockchainInfo, maybeAnchors)
    } yield {
      val anchors = responseForm match {
        case AnchorsNoPath => shortestPathAsJValue(decoratedAnchors)
        case AnchorsWithPath => shortestPathAsJValue(path, decoratedAnchors)
      }

      //This is set here, so we can eliminate a false positive response for when there is no upp record BUT the result is not an error
      val result = maybeEventLog.map { ev =>
        LookupResult.Found(value, queryType, ev.event, anchors)
      }.getOrElse {
        LookupResult.NotFound(value, queryType)
      }

      result
    }

    res.recover {
      case e: Exception =>
        logger.error("For Comprehension Res (shortestPath): " + e.getMessage)
        logger.error("For Comprehension Res (shortestPath): ", e)
        LookupResult.NotFound(value, queryType)
    }

  }

  private def decorateBlockchain(blockchainInfo: BlockchainInfo, vertices: Seq[VertexStruct]): Future[Seq[VertexStruct]] = {

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

  private def upperLower(value: String, queryType: QueryType, responseForm: ResponseForm, blockchainInfo: BlockchainInfo): Future[LookupResult] = {

    val res = for {
      (maybeEventLog, upperPath, upperBlocks, lowerPath, lowerBlocks) <- finder.findUPPWithUpperLowerBounds(value, queryType)
      decoratedUpperAnchors <- decorateBlockchain(blockchainInfo, upperBlocks)
      decoratedLowerAnchors <- decorateBlockchain(blockchainInfo, lowerBlocks)
    } yield {

      val anchors = responseForm match {
        case AnchorsNoPath => upperAndLowerAsJValue(decoratedUpperAnchors, decoratedLowerAnchors)
        case AnchorsWithPath => upperAndLowerAsJValue(upperPath, decoratedUpperAnchors, lowerPath, decoratedLowerAnchors)

      }

      //This is set here, so we can eliminate a false positive response for when there is no upp record BUT the result is not an error
      val result = maybeEventLog.map { ev =>
        LookupResult.Found(value, queryType, ev.event, anchors)
      }.getOrElse {
        LookupResult.NotFound(value, queryType)
      }

      result

    }

    res.recover {
      case e: Exception =>
        logger.error("For Comprehension Res (upperLower):  " + e.getMessage)
        logger.error("For Comprehension Res (upperLower):  ", e)
        LookupResult.NotFound(value, queryType)

    }

  }

  private def shortestPathAsJValue(maybeAnchors: Seq[VertexStruct]): JValue =
    LookupJsonSupport.ToJson[Seq[VertexStruct]](maybeAnchors).get

  private def shortestPathAsJValue(path: Seq[VertexStruct], maybeAnchors: Seq[VertexStruct]): JValue = {
    val anchors = Map(Values.SHORTEST_PATH -> path.map(v => v.toDumbVertexStruct), Values.BLOCKCHAINS -> maybeAnchors.map(v => v.toDumbVertexStruct))
    LookupJsonSupport.ToJson(anchors).get
  }

  private def upperAndLowerAsJValue(upperPath: Seq[VertexStruct], upperBlocks: Seq[VertexStruct], lowerPath: Seq[VertexStruct], lowerBlocks: Seq[VertexStruct]): JValue = {
    val anchors = Map(
      Values.UPPER_PATH -> upperPath,
      Values.UPPER_BLOCKCHAINS -> upperBlocks,
      Values.LOWER_PATH -> lowerPath,
      Values.LOWER_BLOCKCHAINS -> lowerBlocks
    )
    LookupJsonSupport.ToJson(anchors).get
  }

  private def upperAndLowerAsJValue(upperBlocks: Seq[VertexStruct], lowerBlocks: Seq[VertexStruct]): JValue = {
    val anchors = Map(
      Values.UPPER_BLOCKCHAINS -> upperBlocks,
      Values.LOWER_BLOCKCHAINS -> lowerBlocks
    )
    LookupJsonSupport.ToJson(anchors).get
  }

}
