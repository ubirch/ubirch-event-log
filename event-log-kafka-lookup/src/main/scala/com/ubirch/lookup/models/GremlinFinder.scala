package com.ubirch.lookup.models

import java.util.Date

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.lookup.services.Gremlin
import com.ubirch.models.Values
import com.ubirch.util.TimeHelper
import gremlin.scala.{ Key, P, StepLabel, Vertex }
import javax.inject._

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }

class GremlinFinder @Inject() (gremlin: Gremlin)(implicit ec: ExecutionContext) extends LazyLogging {

  import gremlin._

  /**
    * Same as findUpperAndLower but decorates the vertices in the path
    * @param id Id of the vertex
    * @return
    */
  def findUpperAndLowerAsVertices(id: String): Future[(List[VertexStruct], List[VertexStruct], List[VertexStruct], List[VertexStruct])] =
    for {
      (shortest, upper, lowerPath, lower) <- findUpperAndLower(id)
      (completePathShortest, completeBlockchainsUpper) <- asVerticesDecorated(shortest, upper)
      (completePathLower, completeBlockchainsLower) <- asVerticesDecorated(lowerPath, lower)
    } yield {
      (completePathShortest, completeBlockchainsUpper, completePathLower, completeBlockchainsLower)
    }

  /**
    * Find the upper and lower bound blockchains associated to the given vertex
    * @param id Id of the vertex
    * @return A Future of 4 vertex List corresponding to the shortest path, upper path, lower path and ???
    */
  def findUpperAndLower(id: String): Future[(List[Vertex], List[Vertex], List[Vertex], List[Vertex])] = {
    val futureShortestPath: Future[PathHelper] = shortestPathFromVertexToBlockchain(id).map(PathHelper)
    val headTimestamp: Future[Option[Long]] = futureShortestPath.map(_.headOption).flatMap {
      case Some(v) =>
        getTimestampFromVertexAsDate(v)
          .map(_.map(_.getTime))
          .recoverWith {
            case _: Exception =>
              logger.warn("Couldn't parse as Date. Defaulting to Long ")
              getTimestampFromVertexAsLong(v)
          }
      case None => Future.successful(None)
    }

    val maybeLastMasterAndTime: Future[Option[(Vertex, Long)]] = for {
      time <- headTimestamp
      master <- futureShortestPath.map(_.reversedTailHeadOption)
    } yield {
      master.map(x => (x, time.getOrElse(-1L)))
    }

    val upper: Future[List[Vertex]] = maybeLastMasterAndTime.flatMap {
      case Some((v, _)) =>
        getBlockchainsFromMasterVertex(v)
      case None =>
        Future.successful(Nil)
    }

    val lowerPathHelper: Future[Option[(PathHelper, Long)]] = maybeLastMasterAndTime.flatMap {
      case Some((v, t)) => outLT(v, t).map(x => Option((PathHelper(x), t)))
      case None => Future.successful(None)
    }

    val lower: Future[List[Vertex]] = lowerPathHelper.flatMap {
      case Some((ph, _)) =>
        ph.reversedHeadOption
          .map(x => getBlockchainsFromMasterVertex(x))
          .getOrElse(Future.successful(Nil))
      case None =>
        Future.successful(Nil)
    }

    for {
      path <- futureShortestPath.map(_.reversedTailReversed)
      up <- upper
      lp <- lowerPathHelper
      lw <- lower
    } yield {
      (path, up, lp.map(x => x._1).map(_.path).getOrElse(Nil), lw)
    }

  }

  /**
    * Look for a vertex having the matchProperty:value and return the value of his returnProperty
    * @param matchProperty Key of the lookup property
    * @param value Value of the lookup property
    * @param returnProperty Key of the desired property
    * @return Value of the desired property
    */
  def simpleFind(matchProperty: String, value: String, returnProperty: String) =
    g.V()
      .has(Key[String](matchProperty.toLowerCase()), value)
      .value(Key[String](returnProperty.toLowerCase()))
      .promise()

  def getTimestampFromVertexAsLong(vertex: Vertex): Future[Option[Long]] =
    g.V(vertex)
      .value[Long](Values.TIMESTAMP)
      .promise()
      .map(_.headOption)

  def getTimestampFromVertexAsDate(vertex: Vertex): Future[Option[Date]] =
    g.V(vertex)
      .value[Date](Values.TIMESTAMP)
      .promise()
      .map(_.headOption)

  def outLT(master: Vertex, time: Long) = {
    val timestamp = Key[Date](Values.TIMESTAMP)
    g.V(master)
      .repeat(
        _.out(
          Values.MASTER_TREE_CATEGORY + "->" + Values.SLAVE_TREE_CATEGORY,
          Values.MASTER_TREE_CATEGORY + "->" + Values.MASTER_TREE_CATEGORY
        ) // In for other direction
          .simplePath()
      )
      .until(
        _.inE(Values.PUBLIC_CHAIN_CATEGORY + "->" + Values.MASTER_TREE_CATEGORY) // using vertex centered index
          .has(timestamp, P.lt(new Date(time))) // Other P values like P.gt
      )
      .path()
      .limit(1)
      .unfold[Vertex]()
      .promise()
  }

  def shortestPathFromVertexToBlockchain(hash: String) = shortestPath(Values.HASH, hash, Values.PUBLIC_CHAIN_CATEGORY)

  def shortestPath(property: String, value: String, untilLabel: String) = {
    val x = StepLabel[java.util.Set[Vertex]]("x")
    //g.V().has("hash", hash).store("x").repeat(__.in().where(without("x")).aggregate("x")).until(hasLabel("PUBLIC_CHAIN")).limit(1).path().profile()
    g.V()
      .has(Key[String](property.toLowerCase()), value)
      .store(x)
      .repeat(_.in().where(P.without[String](List("x"))).aggregate(x))
      .until(_.hasLabel(untilLabel))
      .limit(1)
      .path()
      .unfold[Vertex]()
      .promise()
  }

  def asVertices(path: List[Vertex], anchors: List[Vertex]) = {

    def withPrevious(hashes: List[Any]) = Map(Values.PREV_HASH -> hashes.mkString(","))
    def withNext(hashes: List[Any]) = Map(Values.NEXT_HASH -> hashes.mkString(","))

    for {
      pathV <- toVertexStruct(path)
      blockchainsV <- toVertexStruct(anchors)
    } yield {

      val pathLinks =
        pathV
          .foldLeft(List.empty[VertexStruct]) { (acc, current) =>

            val next = acc
              .reverse
              .headOption
              .map(_.properties)
              .flatMap(_.get(Values.HASH))
              .map(x => withPrevious(List(x)))
              .getOrElse(withPrevious(List("")))

            acc ++ List(current.addProperties(next))
          }
          .foldRight(List.empty[VertexStruct]) { (current, acc) =>

            val next = acc
              .headOption
              .map(_.properties)
              .flatMap(_.get(Values.HASH))
              .map(x => withNext(List(x)))
              .getOrElse(withNext(List("")))

            current.addProperties(next) +: acc
          }

      val blockchainHashes = blockchainsV.map(_.properties.getOrElse(Values.HASH, ""))
      val upToMaster = pathLinks.reverse
      val lastMaster = upToMaster.headOption.map(_.addProperties(withNext(blockchainHashes))).toList
      val lastMasterHash = lastMaster.map(x => x.properties.getOrElse(Values.HASH, ""))

      val completePath = {
        val cp = if (upToMaster.isEmpty) Nil
        else upToMaster.tail.reverse

        cp ++ lastMaster
      }
      val completeBlockchains = blockchainsV.map(_.addProperties(withPrevious(lastMasterHash)))

      (completePath, completeBlockchains)
    }
  }

  def asVerticesDecorated(path: List[Vertex], anchors: List[Vertex]) = {
    def parseTimestamp(anyTime: Any): String = {
      anyTime match {
        case time if anyTime.isInstanceOf[Long] =>
          TimeHelper.toIsoDateTime(time.asInstanceOf[Long])
        case time if anyTime.isInstanceOf[Date] =>
          TimeHelper.toIsoDateTime(time.asInstanceOf[Date].getTime)
        case time =>
          time.asInstanceOf[String]
      }
    }
    //Uncomment and use if needed
    //    def decorateType(anyTime: Any): Any = {
    //      anyTime match {
    //        case time if anyTime.isInstanceOf[String] =>
    //          if (time.toString == Values.SLAVE_TREE_CATEGORY) Values.FOUNDATION_TREE_CATEGORY
    //          else time.toString
    //        case time => time
    //      }
    //    }
    asVertices(path, anchors).map { case (p, a) =>
      val _path = p.map(x =>
        x.map(Values.TIMESTAMP)(parseTimestamp)
          .addLabelWhen(Values.FOUNDATION_TREE_CATEGORY)(Values.SLAVE_TREE_CATEGORY))

      val _anchors = a.map(_.map(Values.TIMESTAMP)(parseTimestamp))

      (_path, _anchors)
    }
  }

  def toVertexStruct(vertices: List[Vertex]) = {
    val futureRes = vertices.map { v =>
      val gremlinRes = for {
        jmaps <- g.V(v).valueMap().promise().map(_.headOption)
        label <- g.V(v).label().promise().map(_.headOption)
      } yield {
        val maps = jmaps
          .map(_.asScala.toMap)
          .map(_.map { x =>
            try {
              val key = x._1.toString
              val value = x._2.asInstanceOf[java.util.ArrayList[Any]].asScala.headOption.getOrElse("NO VALUE")
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

    Future.sequence(futureRes)
      .map { xs => xs.flatMap(y => y.toList) }
      .map { xs => xs.map { case (a, b) => VertexStruct(a, b) } }
  }

  def findAnchorsWithPathAsVertices(id: String) =
    for {
      (path, anchors) <- findAnchorsWithPath(id)
      (completePath, completeBlockchains) <- asVerticesDecorated(path, anchors)
    } yield {
      (completePath, completeBlockchains)
    }

  def findAnchorsWithPath(id: String): Future[(List[Vertex], List[Vertex])] = {
    val futureShortestPath: Future[PathHelper] = shortestPathFromVertexToBlockchain(id).map(PathHelper)

    val maybeBlockchains = futureShortestPath.map(_.reversedTailHeadOption).flatMap {
      case Some(v) => getBlockchainsFromMasterVertex(v)
      case None => Future.successful(Nil)
    }

    for {
      sp <- futureShortestPath.map(_.reversedTailReversed)
      bcs <- maybeBlockchains
    } yield (sp, bcs)

  }

  def getBlockchainsFromMasterVertexFromOption(master: Option[Vertex]) =
    master match {
      case Some(value) => getBlockchainsFromMasterVertex(value)
      case None => Future.successful(Nil)
    }

  def getBlockchainsFromMasterVertex(master: Vertex) =
    g.V(master)
      .in()
      .hasLabel(Values.PUBLIC_CHAIN_CATEGORY)
      .promise()

  case class PathHelper(path: List[Vertex]) {
    lazy val reversed = path.reverse
    lazy val headOption = path.headOption
    lazy val reversedHeadOption = reversed.headOption
    lazy val reversedTail =
      if (reversed.isEmpty) Nil
      else reversed.tail

    lazy val reversedTailReversed = reversedTail.reverse
    lazy val reversedTailHeadOption = reversedTail.headOption
  }

}
