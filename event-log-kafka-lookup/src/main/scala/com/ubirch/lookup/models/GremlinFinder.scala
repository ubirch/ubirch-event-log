package com.ubirch.lookup.models

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.lookup.services.Gremlin
import com.ubirch.models.Values
import gremlin.scala.{ Key, Vertex }
import javax.inject._

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }

class GremlinFinder @Inject() (gremlin: Gremlin)(implicit ec: ExecutionContext) extends LazyLogging {

  import gremlin._

  def shortestPath(property: String, value: String, untilLabel: String) = {
    g.V()
      .has(Key[String](property.toLowerCase()), value)
      .repeat(_.in().simplePath())
      .until(_.hasLabel(untilLabel))
      .path()
      .limit(1)
      .unfold[Vertex]()
      .promise()
  }

  def findAnchorsWithPath(id: String) = {

    val futureShortestPath = shortestPath(Values.HASH, id, Values.PUBLIC_CHAIN_CATEGORY)
    val reversedPath = futureShortestPath.map(_.reverse)
    val reversedPathNoEnd = reversedPath.map { x =>
      if (x.isEmpty) Nil
      else x.tail
    }
    val shortestPathNoEnd = reversedPathNoEnd.map(_.reverse)
    val maybeLastMaster = reversedPathNoEnd.map(_.headOption)

    val maybeBlockchains = maybeLastMaster.flatMap {
      case Some(v) =>
        g.V(v)
          .in()
          .hasLabel(Values.PUBLIC_CHAIN_CATEGORY)
          .promise()
      case None => Future.successful(Nil)
    }

    for {
      sp <- shortestPathNoEnd
      bcs <- maybeBlockchains
    } yield {
      (sp, bcs)
    }

  }

  def toVertexStruct(vertices: List[Vertex]) = {
    val futureRes = vertices.map { v =>
      val fmaps = g.V(v).valueMap().promise().map(_.headOption)
      val flabel = g.V(v).label().promise().map(_.headOption)
      val gremlinRes = for {
        jmaps <- fmaps
        label <- flabel
      } yield {
        val maps = jmaps
          .map(_.asScala.toMap)
          .map(_.map { x =>
            try {
              val key = x._1.toString
              val value = x._2.asInstanceOf[java.util.ArrayList[String]].asScala.headOption.getOrElse("NO VALUE")
              key -> value
            } catch {
              case e: Exception =>
                logger.error("Error creating VertexStruct")
                throw e
            }
          })
          .getOrElse(Map.empty[String, String])
        label.map(x => (x, maps))
      }
      gremlinRes
    }

    Future.sequence(futureRes).map { xs =>
      xs.flatMap(y => y.toList)
    }.map { xs =>
      xs.map { case (a, b) =>
        VertexStruct(a, b)
      }
    }
  }

  def findAnchorsWithPathAsVertices(id: String) = for {
    (path, blockchains) <- findAnchorsWithPath(id)
    pathV <- toVertexStruct(path)
    blockchainsV <- toVertexStruct(blockchains)
  } yield {

    def withPrevious(hash: String) = Map(Values.PREV_HASH -> hash)
    def withNext(hash: String) = Map(Values.NEXT_HASH -> hash)

    val pathWithPrevious = {
      pathV.foldLeft(List.empty[VertexStruct]){
        (acc, current) =>

          val next = acc.reverse
            .headOption
            .map(_.properties)
            .flatMap(_.get(Values.HASH))
            .map(withPrevious)
            .getOrElse(withPrevious(""))

          acc  ++ List(current.copy(properties = current.properties ++ next))
      }
    }

    val pathWithNext = {
      pathWithPrevious.foldRight(List.empty[VertexStruct]){
        (acc, current) =>

          val next = current
            .headOption
            .map(_.properties)
            .flatMap(_.get(Values.HASH))
            .map(withNext)
            .getOrElse(withNext(""))

          List(acc.copy(properties = acc.properties ++ next)) ++ current
      }
    }

    (pathWithNext, blockchainsV)
  }

}
