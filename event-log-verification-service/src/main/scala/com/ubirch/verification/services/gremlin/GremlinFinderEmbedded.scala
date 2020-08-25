package com.ubirch.verification.services.gremlin

import java.util.Date

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.Values
import com.ubirch.verification.models.VertexStruct
import com.ubirch.verification.services.gremlin.GremlinFinder._
import gremlin.scala.Key
import javax.inject._

import scala.concurrent.{ ExecutionContext, Future }

/**
  * This finder is to be used when using an embedded janusgraph
  */
@Singleton
class GremlinFinderEmbedded @Inject() (gremlin: Gremlin, config: Config)(implicit ec: ExecutionContext) extends GremlinFinder with LazyLogging {

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
    Future.successful(gremlin.g.V()
      .simpleFindReturnElement(matchProperty.toLowerCase(), value, returnProperty.toLowerCase())
      .l())

  /**
    * This function finds a master tree (connected to blockchains) whose date is inferior to the one passed as the argument
    * The starting point is the master that is passed as a parameter.
    * @param master Starting point of the search.
    * @param time Upper bound time of the queried master tree.
    * @return The path between the starting point and the master tree.
    */
  def outLT(master: VertexStruct, time: Long): List[VertexStruct] = {
    val res = gremlin.g.V(master.id)
      .toPreviousMasterWithBlockchain(time)
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
  def shortestPath(property: String, value: String, untilLabel: String): List[VertexStruct] = {
    //val x = StepLabel[java.util.Set[Vertex]]("x")
    //g.V().has("hash", hash).store("x").repeat(__.in().where(without("x")).aggregate("x")).until(hasLabel("PUBLIC_CHAIN")).limit(1).path().profile()
    val shortestPath = gremlin.g.V()
      .findVertex(property.toLowerCase(), value)
      .shortestPathToLabel(untilLabel)
      .l()
    shortestPath.map(v => VertexStruct.fromMap(v))
  }

  /**
    * Same as shortestPath but optimized for querying between upp and blockchain
    */
  def shortestPathUppBlockchain(property: String, value: String): List[VertexStruct] = {
    //val x = StepLabel[java.util.Set[Vertex]]("x")
    //g.V().has("hash", hash).store("x").repeat(__.in().where(without("x")).aggregate("x")).until(hasLabel("PUBLIC_CHAIN")).limit(1).path().profile()
    val shortestPath = gremlin.g.V()
      .findVertex(property.toLowerCase(), value)
      .shortestPathUppBlockchain
      .l()
    shortestPath.map(v => VertexStruct.fromMap(v))
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
      .blockchainsFromMasterVertex
      .l()
    blockchains.map(b => VertexStruct.fromMap(b))
  }

}
