package com.ubirch.lookup.models

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.lookup.util.LookupJsonSupport
import com.ubirch.models.EventLogRow
import javax.inject._
import org.json4s.JsonAST.JValue

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

@Singleton
class Finder @Inject() (cassandraFinder: CassandraFinder, gremlinFinder: GremlinFinder)(implicit ec: ExecutionContext) extends LazyLogging {

  def findAll(value: String, queryType: QueryType): Future[(Option[EventLogRow], Seq[JValue], Seq[JValue])] = {
    val fres = cassandraFinder.findUPP(value, queryType).flatMap {
      case upp @ Some(uppEl) =>
        gremlinFinder.findAnchorsWithPathAsVertices(uppEl.id)
          .map { case (path, blockchains) =>
            (upp, path.map(x => LookupJsonSupport.ToJson[VertexStruct](x).get), blockchains.map(x => LookupJsonSupport.ToJson[VertexStruct](x).get))
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

}
