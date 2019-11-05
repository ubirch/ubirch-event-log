package com.ubirch.lookup.models

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.EventLogRow
import javax.inject._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

trait Finder extends LazyLogging {

  implicit def ec: ExecutionContext

  def findEventLog(value: String, category: String): Future[Option[EventLogRow]]

  def findUPP(value: String, queryType: QueryType): Future[Option[EventLogRow]]

  def findUPPWithShortestPath(value: String, queryType: QueryType): Future[(Option[EventLogRow], Seq[VertexStruct], Seq[VertexStruct])] = {
    val fres = findUPP(value, queryType).flatMap {
      case upp @ Some(uppEl) =>

        findAnchorsWithPathAsVertices(uppEl.id)
          .map { case (path, blockchains) => (upp, path, blockchains) }
          .recover {
            case e: Exception =>
              logger.error("Error talking Gremlin= {}", e.getMessage)
              (upp, List.empty, List.empty)
          }

      case None => Future.successful((None, Seq.empty, Seq.empty))
    }

    fres.onComplete {

      case Success(res) =>
        logger.debug("Received a [{}] request with value [{}] and result [{}]", queryType.value, value, res.toString)

      case Failure(exception) =>
        logger.error("Received a [{}] request with value [{}] and result [{}]", queryType.value, value, exception.getMessage)

    }

    fres
  }

  def findUPPWithUpperLowerBounds(value: String, queryType: QueryType): Future[(Option[EventLogRow], Seq[VertexStruct], Seq[VertexStruct], Seq[VertexStruct], Seq[VertexStruct])] = {
    val fres = findUPP(value, queryType).flatMap {
      case upp @ Some(uppEl) =>

        findUpperAndLowerAsVertices(uppEl.id)
          .map { case (upperPath, upperBlocks, lowerPath, lowerBlocks) =>
            (upp, upperPath, upperBlocks, lowerPath, lowerBlocks)
          }
          .recover {
            case e: Exception =>
              logger.error("Error talking Gremlin= {}", e.getMessage)
              (upp, List.empty, List.empty, List.empty, List.empty)
          }

      case None => Future.successful((None, Seq.empty, Seq.empty, Seq.empty, Seq.empty))
    }

    fres.onComplete {

      case Success(res) =>
        logger.debug("Received a [{}] request with value [{}] and result [{}]", queryType.value, value, res.toString)

      case Failure(exception) =>
        logger.error("Received a [{}] request with value [{}] and result [{}]", queryType.value, value, exception.getMessage)

    }

    fres
  }

  def findAnchorsWithPathAsVertices(id: String): Future[(List[VertexStruct], List[VertexStruct])]

  def findUpperAndLowerAsVertices(id: String): Future[(List[VertexStruct], List[VertexStruct], List[VertexStruct], List[VertexStruct])]

}

@Singleton
class DefaultFinder @Inject() (cassandraFinder: CassandraFinder, gremlinFinder: GremlinFinder)(implicit val ec: ExecutionContext)
  extends Finder
  with LazyLogging {

  def findEventLog(value: String, category: String): Future[Option[EventLogRow]] = cassandraFinder.findEventLog(value, category)

  def findUPP(value: String, queryType: QueryType): Future[Option[EventLogRow]] = cassandraFinder.findUPP(value, queryType)

  def findAnchorsWithPathAsVertices(id: String): Future[(List[VertexStruct], List[VertexStruct])] = gremlinFinder.findAnchorsWithPathAsVertices(id)

  def findUpperAndLowerAsVertices(id: String): Future[(List[VertexStruct], List[VertexStruct], List[VertexStruct], List[VertexStruct])] = gremlinFinder.findUpperAndLowerAsVertices(id)

}
