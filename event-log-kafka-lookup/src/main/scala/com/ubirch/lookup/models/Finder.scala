package com.ubirch.lookup.models

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.lookup.util.LookupJsonSupport
import com.ubirch.models.EventLogRow
import javax.inject._
import org.json4s.JsonAST.JValue

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

trait Finder extends LazyLogging {

  implicit def ec: ExecutionContext

  def findUPP(value: String, queryType: QueryType): Future[Option[EventLogRow]]

  def findAll(value: String, queryType: QueryType): Future[(Option[EventLogRow], Seq[VertexStruct], Seq[VertexStruct])] = {
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
        logger.debug("Received a [{}] request with value [{}] and result [{}]", queryType.value, value, res.toString())

      case Failure(exception) =>
        logger.error("Received a [{}] request with value [{}] and result [{}]", queryType.value, value, exception.getMessage)

    }

    fres
  }

  def findAnchorsWithPathAsVertices(id: String): Future[(List[VertexStruct], List[VertexStruct])]

}

@Singleton
class DefaultFinder @Inject() (cassandraFinder: CassandraFinder, gremlinFinder: GremlinFinder)(implicit val ec: ExecutionContext)
  extends Finder
  with LazyLogging {

  def findUPP(value: String, queryType: QueryType): Future[Option[EventLogRow]] = cassandraFinder.findUPP(value, queryType)

  def findAnchorsWithPathAsVertices(id: String): Future[(List[VertexStruct], List[VertexStruct])] = gremlinFinder.findAnchorsWithPathAsVertices(id)

}
