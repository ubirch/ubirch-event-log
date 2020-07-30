package com.ubirch.verification.service.services

import java.util.Date

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.Values
import com.ubirch.util.TimeHelper
import com.ubirch.verification.service.models.VertexStruct
import gremlin.scala.{ Key, P, StepLabel, Vertex }
import javax.inject._

import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }

class GremlinFinder @Inject() (gremlin: Gremlin)(implicit ec: ExecutionContext) extends LazyLogging {

  /**
    * Same as findUpperAndLower but decorates the vertices in the path
    *
    * @param id Id of the vertex
    * @return
    */
  def findUpperAndLowerAsVertices(id: String): Future[(List[VertexStruct], List[VertexStruct], List[VertexStruct], List[VertexStruct])] =
    for {
      (shortest, upper, lowerPath, lower) <- findUpperAndLower(id)
    } yield {
      val (completePathShortest, completeBlockchainsUpper) = asVerticesDecorated(shortest, upper)
      val (completePathLower, completeBlockchainsLower) = asVerticesDecorated(lowerPath, lower)
      (completePathShortest, completeBlockchainsUpper, completePathLower, completeBlockchainsLower)
    }

  /**
    * Find the upper and lower bound blockchains associated to the given vertex
    *
    * @param id Id of the vertex
    * @return A Future of 4 vertex List corresponding to the shortest path, upper path, lower path and ???
    */
  def findUpperAndLower(id: String): Future[(List[VertexStruct], List[VertexStruct], List[VertexStruct], List[VertexStruct])] = {
    val futureShortestPath: Future[PathHelper] = shortestPathFromVertexToBlockchain(id).map(PathHelper)
    val headTimestamp: Future[Option[Long]] = futureShortestPath.map(_.headOption).flatMap {
      case Some(v) =>
        getTimestampFromVertexAsDate(v) match {
          case Some(value) => Future.successful(Some(value.getTime))
          case None => Future.successful(getTimestampFromVertexAsLong(v))
        }
      case None => Future.successful(None)
    }

    val maybeLastMasterAndTime: Future[Option[(VertexStruct, Long)]] = for {
      time <- headTimestamp
      master <- futureShortestPath.map(_.reversedTailHeadOption)
    } yield {
      master.map(x => (x, time.getOrElse(-1L)))
    }

    val upper: Future[List[VertexStruct]] = maybeLastMasterAndTime.flatMap {
      case Some((v, _)) =>
        getBlockchainsFromMasterVertex(v)
      case None =>
        Future.successful(Nil)
    }

    val lowerPathHelper: Future[Option[(PathHelper, Long)]] = maybeLastMasterAndTime.flatMap {
      case Some((v, t)) => outLT(v, t).map(x => Option(PathHelper(x), t))
      case None => Future.successful(None)
    }

    val lower: Future[List[VertexStruct]] = lowerPathHelper.flatMap {
      case Some((ph, t)) =>
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
      (path.toList, up, lp.map(x => x._1).map(_.path).getOrElse(Nil), lw)
    }

  }

  /**
    * Look for a vertex having the matchProperty:value and return the value of his returnProperty
    *
    * @param matchProperty  Key of the lookup property
    * @param value          Value of the lookup property
    * @param returnProperty Key of the desired property
    * @return Value of the desired property
    */
  def simpleFind(matchProperty: String, value: String, returnProperty: String): Future[List[String]] =
    gremlin.g.V()
      .has(Key[String](matchProperty.toLowerCase()), value)
      .value(Key[String](returnProperty.toLowerCase()))
      .promise()

  def getTimestampFromVertexAsLong(vertex: VertexStruct): Option[Long] =
    vertex.properties.get("timestamp") match {
      case Some(value) =>
        value match {
          case date: String => Some(date.toLong)
          case _ => None
        }
      case None => None
    }

  def getTimestampFromVertexAsDate(vertex: VertexStruct): Option[Date] =
    vertex.properties.get("timestamp") match {
      case Some(value) =>
        value match {
          case date: Date => Some(date)
          case _ => None
        }
      case None => None
    }

  def outLT(master: VertexStruct, time: Long): Future[List[VertexStruct]] = {
    val timestamp = Key[Date](Values.TIMESTAMP)
    val date = new Date(time)
    val res = gremlin.g.V(master.id)
      .repeat(
        _.out(
          Values.MASTER_TREE_CATEGORY + "->" + Values.SLAVE_TREE_CATEGORY,
          Values.MASTER_TREE_CATEGORY + "->" + Values.MASTER_TREE_CATEGORY
        ) // In for other direction
          .simplePath()
      )
      .until(
        _.inE(Values.PUBLIC_CHAIN_CATEGORY + "->" + Values.MASTER_TREE_CATEGORY) // using vertex centered index
          .has(timestamp, P.lt(date)) // Other P values like P.gt
      )
      .limit(1)
      .path()
      .unfold[Vertex]()
      .elementMap
      .promise()
    for {
      vertices <- res
    } yield {
      vertices.map(v => VertexStruct.fromMap(v))
    }
  }

  def shortestPathFromVertexToBlockchain(hash: String): Future[List[VertexStruct]] = shortestPath(Values.HASH, hash, Values.PUBLIC_CHAIN_CATEGORY)

  def shortestPath(property: String, value: String, untilLabel: String): Future[List[VertexStruct]] = {
    //val x = StepLabel[java.util.Set[Vertex]]("x")
    //g.V().has("hash", hash).store("x").repeat(__.in().where(without("x")).aggregate("x")).until(hasLabel("PUBLIC_CHAIN")).limit(1).path().profile()
    val shortestPath = gremlin.g.V()
      .has(Key[String](property.toLowerCase()), value)
      //.store(x)
      .repeat(_.in(
        Values.PUBLIC_CHAIN_CATEGORY + "->" + Values.MASTER_TREE_CATEGORY,
        Values.MASTER_TREE_CATEGORY + "->" + Values.MASTER_TREE_CATEGORY,
        Values.MASTER_TREE_CATEGORY + "->" + Values.SLAVE_TREE_CATEGORY,
        Values.SLAVE_TREE_CATEGORY + "->" + Values.SLAVE_TREE_CATEGORY,
        Values.SLAVE_TREE_CATEGORY + "->" + Values.UPP_CATEGORY
      ).simplePath())
      .until(_.hasLabel(untilLabel))
      .limit(1)
      .path()
      .unfold[Vertex]()
      .elementMap
      .promise()
    for {
      sp <- shortestPath
    } yield {
      sp.map(v => {
        VertexStruct.fromMap(v)
      })
    }
  }

  def asVertices(path: List[VertexStruct], anchors: List[VertexStruct]): (List[VertexStruct], List[VertexStruct]) = {

    def withPrevious(hashes: List[Any]) = Map(Values.PREV_HASH -> hashes.mkString(","))

    def withNext(hashes: List[Any]) = Map(Values.NEXT_HASH -> hashes.mkString(","))

    val pathLinks =
      path
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

    val blockchainHashes = anchors.map(_.properties.getOrElse(Values.HASH, ""))
    val upToMaster = pathLinks.reverse
    val lastMaster = upToMaster.headOption.map(_.addProperties(withNext(blockchainHashes))).toList
    val lastMasterHash = lastMaster.map(x => x.properties.getOrElse(Values.HASH, ""))

    val completePath = {
      val cp = if (upToMaster.isEmpty) Nil
      else upToMaster.tail.reverse

      cp ++ lastMaster
    }
    val completeBlockchains = anchors.map(_.addProperties(withPrevious(lastMasterHash)))

    (completePath, completeBlockchains)

  }

  def asVerticesDecorated(path: List[VertexStruct], anchors: List[VertexStruct]): (List[VertexStruct], List[VertexStruct]) = {
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

    def decorateType(anyTime: Any): Any = {
      anyTime match {
        case time if anyTime.isInstanceOf[String] =>
          if (time.toString == Values.SLAVE_TREE_CATEGORY) Values.FOUNDATION_TREE_CATEGORY
          else time.toString
        case time => time
      }
    }

    asVertices(path, anchors) match { case (p, a) =>
      val _path = p.map(x =>
        x.map(Values.TIMESTAMP)(parseTimestamp)
          .addLabelWhen(Values.FOUNDATION_TREE_CATEGORY)(Values.SLAVE_TREE_CATEGORY))

      val _anchors = a.map(_.map(Values.TIMESTAMP)(parseTimestamp))

      (_path, _anchors)
    }
  }

  def findAnchorsWithPathAsVertices(id: String): Future[(List[VertexStruct], List[VertexStruct])] =
    for {
      (path, anchors) <- findAnchorsWithPath(id)
    } yield {
      asVerticesDecorated(path, anchors)
    }

  def findAnchorsWithPath(id: String): Future[(List[VertexStruct], List[VertexStruct])] = {
    val futureShortestPath: Future[PathHelper] = shortestPathFromVertexToBlockchain(id).map(PathHelper)

    val maybeBlockchains = futureShortestPath.map(_.reversedTailHeadOption).flatMap {
      case Some(v) => getBlockchainsFromMasterVertex(v)
      case None => Future.successful(Nil)
    }

    for {
      sp <- futureShortestPath.map(_.reversedTailReversed)
      bcs <- maybeBlockchains
    } yield (sp.toList, bcs)

  }

  def getBlockchainsFromMasterVertex(master: VertexStruct): Future[List[VertexStruct]] = {
    val futureBlockchains = gremlin.g.V(master.id)
      .in()
      .hasLabel(Values.PUBLIC_CHAIN_CATEGORY)
      .elementMap
      .promise()
    for {
      blockchains <- futureBlockchains
    } yield {
      blockchains.map(b => VertexStruct.fromMap(b))
    }
  }

  case class PathHelper(path: List[VertexStruct]) {
    lazy val reversed: Seq[VertexStruct] = path.reverse
    lazy val headOption: Option[VertexStruct] = path.headOption
    lazy val reversedHeadOption: Option[VertexStruct] = reversed.headOption
    lazy val reversedTail: Seq[VertexStruct] =
      if (reversed.isEmpty) Nil
      else reversed.tail

    lazy val reversedTailReversed: Seq[VertexStruct] = reversedTail.reverse
    lazy val reversedTailHeadOption: Option[VertexStruct] = reversedTail.headOption
  }

}
