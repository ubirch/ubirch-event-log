package com.ubirch.verification.services

import java.util.Date

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.Values
import com.ubirch.util.TimeHelper
import com.ubirch.verification.models.VertexStruct
import gremlin.scala.{ Key, P, Vertex }
import javax.inject._

import scala.concurrent.{ ExecutionContext, Future }

class GremlinFinder @Inject() (gremlin: Gremlin, config: Config)(implicit ec: ExecutionContext) extends LazyLogging {

  /*
  This variable is here to select which algorithm will be used to find the lower path: going back from the first found
  Master Tree (false), or the last found (true)
   */
  lazy val safeMode: Boolean = config.getBoolean("eventLog.gremlin.safeMode")

  /**
    * Find the upper and lower bound blockchains associated to the given vertex and decorates the vertices in the path.
    *
    * @param id Id of the vertex
    * @return
    */
  def findUpperAndLowerAsVertices(id: String): Future[(List[VertexStruct], List[VertexStruct], List[VertexStruct], List[VertexStruct])] = {
    val (shortest, upper, lowerPath, lower) = findUpperAndLower(id)
    val (completePathShortest, completeBlockchainsUpper) = asVerticesDecorated(shortest, upper)
    val (completePathLower, completeBlockchainsLower) = asVerticesDecorated(lowerPath, lower)
    Future.successful(completePathShortest, completeBlockchainsUpper, completePathLower, completeBlockchainsLower)
  }

  /**
    * Find the upper and lower bound blockchains associated to the given vertex
    * Bellow an example of the final path.
    * MT1 = first master tree found. Correspond to maybeFirstMasterAndTime
    * MT2 = last master tree found. Correspond to maybeLastMasterAndTime
    * BCU1/2 = example of 2 blockchains transactions associated to the master tree 2. Correspond to the upper bound
    * There the anchoring time will be posterior than the created time of the UPP
    * BCL1/BCL2 = example of 2 blockchains transactions associated to the master tree 2. Correspond to the lower bound
    * There the anchoring time will be anterior than the created time of the UPP
    *
    *        BCL1/BCL2                                      BCU1/BCU2
    *            |                                              |
    *           MT0-----MT----MT----MT----MT1-----MT-----MT----MT2
    *                                      |
    *               FT---FT---FT----FT----FT
    *               |
    *              UPP
    *
    * @param id Id of the vertex
    * @return A Future of 4 vertex List corresponding to the upper path, upper blockhains, lower path and lower blockchains
    */
  def findUpperAndLower(id: String): (List[VertexStruct], List[VertexStruct], List[VertexStruct], List[VertexStruct]) = {
    val shortestPath: PathHelper = PathHelper(shortestPathFromVertexToBlockchain(id))
    val headTimestamp: Option[Long] = shortestPath.headOption match {
      case Some(v) =>
        getTimestampFromVertexAsDate(v) match {
          case Some(value) => Some(value.getTime)
          case None => getTimestampFromVertexAsLong(v)
        }
      case None => None
    }

    val maybeLastMasterAndTime: Option[(VertexStruct, Long)] = shortestPath
      .reversedTailHeadOption
      .map(x => (x, headTimestamp.getOrElse(-1L)))

    val upperBlockchains: List[VertexStruct] = maybeLastMasterAndTime match {
      case Some((v, _)) =>
        getBlockchainsFromMasterVertex(v)
      case None =>
        Nil
    }

    val lowerPathHelper: Option[(PathHelper, Long)] = {
      if (safeMode) {
        maybeLastMasterAndTime match {
          case Some((v, t)) => Option(PathHelper(outLT(v, t)), t)
          case None => None
        }
      } else {
        val maybeFirstMasterAndTime: Option[(VertexStruct, Long)] = shortestPath
          .firstMasterOption
          .map(x => (x, headTimestamp.getOrElse(-1L)))

        maybeFirstMasterAndTime match {
          case Some((v, t)) => Option(PathHelper(outLT(v, t)), t)
          case None => None
        }
      }
    }

    val lowerBlockchains: List[VertexStruct] = lowerPathHelper match {
      case Some((ph, _)) =>
        ph.reversedHeadOption
          .map(x => getBlockchainsFromMasterVertex(x))
          .getOrElse(Nil)
      case None =>
        Nil
    }

    (shortestPath.reversedTailReversed.toList, upperBlockchains, lowerPathHelper.map(x => x._1).map(_.path).getOrElse(Nil), lowerBlockchains)

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

  /**
    * This function finds a master tree (connected to blockchains) whose date is inferior to the one passed as the argument
    * The starting point is the master that is passed as a parameter.
    * @param master Starting point of the search.
    * @param time Upper bound time of the queried master tree.
    * @return The path between the starting point and the master tree.
    */
  def outLT(master: VertexStruct, time: Long): List[VertexStruct] = {
    val timestamp = Key[Date](Values.TIMESTAMP)
    val date = new Date(time)
    val res = gremlin.g.V(master.id)
      .repeat(
        _.out(
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
      .l()
    res.map(v => VertexStruct.fromMap(v))
  }

  def shortestPathFromVertexToBlockchain(hash: String): List[VertexStruct] = shortestPathUppBlockchain(Values.HASH, hash)

  /**
    * Will look for the shortest path between the vertice that has the value {@param value} of the property with the
    * name {@param property} until it finds a vertex with the label {@param untilLabel}. This version will only look
    * for younger vertices, as it only goes "in"
    * @param property The name of the starting vertex property.
    * @param value The value of the starting vertex property.
    * @param untilLabel The end label
    * @return The path from the beginning to the end as a VertexStruct list
    */
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

  /**
    * Same as shortestPath but optimized for querying between upp and blockchain
    */
  def shortestPathUppBlockchain(property: String, value: String): List[VertexStruct] = {
    //val x = StepLabel[java.util.Set[Vertex]]("x")
    //g.V().has("hash", hash).store("x").repeat(__.in().where(without("x")).aggregate("x")).until(hasLabel("PUBLIC_CHAIN")).limit(1).path().profile()
    val shortestPath = gremlin.g.V()
      .has(Key[String](property.toLowerCase()), value)
      //.store(x)
      .in(Values.SLAVE_TREE_CATEGORY + "->" + Values.UPP_CATEGORY)
      .repeat(_.in(
        Values.MASTER_TREE_CATEGORY + "->" + Values.SLAVE_TREE_CATEGORY,
        Values.SLAVE_TREE_CATEGORY + "->" + Values.SLAVE_TREE_CATEGORY,
      ).simplePath())
      .until(_.hasLabel(Values.MASTER_TREE_CATEGORY))
      .limit(1)
      .repeat(_.in(
        Values.PUBLIC_CHAIN_CATEGORY + "->" + Values.MASTER_TREE_CATEGORY,
        Values.MASTER_TREE_CATEGORY + "->" + Values.MASTER_TREE_CATEGORY,
      ).simplePath())
      .until(_.hasLabel(Values.PUBLIC_CHAIN_CATEGORY))
      .limit(1)
      .path()
      .unfold[Vertex]()
      .elementMap
      .l()
    shortestPath.map(v => VertexStruct.fromMap(v))
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

    asVertices(path, anchors) match {
      case (p, a) =>
        val _path = p.map(x =>
          x.map(Values.TIMESTAMP)(parseTimestamp)
            .addLabelWhen(Values.FOUNDATION_TREE_CATEGORY)(Values.SLAVE_TREE_CATEGORY))

        val _anchors = a.map(_.map(Values.TIMESTAMP)(parseTimestamp))

        (_path, _anchors)
    }
  }

  def findAnchorsWithPathAsVertices(id: String): Future[(List[VertexStruct], List[VertexStruct])] = {
    val (path, anchors) = findAnchorsWithPath(id)
    Future.successful(asVerticesDecorated(path, anchors))
  }

  def findAnchorsWithPath(id: String): (List[VertexStruct], List[VertexStruct]) = {
    val shortestPath: PathHelper = PathHelper(shortestPathFromVertexToBlockchain(id))

    val maybeBlockchains = shortestPath.reversedTailHeadOption match {
      case Some(v) => getBlockchainsFromMasterVertex(v)
      case None => Nil
    }

    (shortestPath.reversedTailReversed.toList, maybeBlockchains)
  }

  def getBlockchainsFromMasterVertex(master: VertexStruct): List[VertexStruct] = {
    val blockchains = gremlin.g.V(master.id)
      .in()
      .hasLabel(Values.PUBLIC_CHAIN_CATEGORY)
      .elementMap
      .l()
    blockchains.map(b => VertexStruct.fromMap(b))
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
    lazy val firstMasterOption: Option[VertexStruct] = path.find(v => v.label == Values.MASTER_TREE_CATEGORY)
  }

}
