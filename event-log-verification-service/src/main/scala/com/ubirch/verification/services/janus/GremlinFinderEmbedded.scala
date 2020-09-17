package com.ubirch.verification.services.janus

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.Values
import com.ubirch.verification.models.VertexStruct
import com.ubirch.verification.services.janus.GremlinFinder._
import javax.inject._

import scala.concurrent.{ ExecutionContext, Future }

/**
  * This finder is to be used when using an embedded janusgraph
  */
@Singleton
class GremlinFinderEmbedded @Inject() (gremlin: Gremlin, config: Config)(implicit ec: ExecutionContext) extends GremlinFinder with LazyLogging {

  /*
  This variable correspond to the minimum of anchors (ie: blockchains) needed to be found by the
  findUpperAndLowerAsVertices method
    */
  lazy val numberDifferentAnchors: Int = config.getInt("verification.number-different-anchors")

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
    val (upper, lower) = findUpperAndLower(id)
    val (completePathShortest, completeBlockchainsUpper) = asVerticesDecorated(upper)
    val (completePathLower, completeBlockchainsLower) = asVerticesDecorated(lower)
    Future.successful(completePathShortest, completeBlockchainsUpper, completePathLower, completeBlockchainsLower)
  }

  /**
    * Find the upper and lower bound blockchains associated to the given vertex, up to a specified number of different
    * blockchains.
    * Bellow an example of the final path (here, minimum number of different blockchains desired = 3).
    * MT2 = first master tree found. Correspond to maybeFirstMasterAndTime
    * MT3 = last master tree found. Correspond to maybeLastMasterAndTime
    * MT4 = uppest master tree found that has a blockchain and whose blockchain name is different than the ones anchored
    * in previously found master trees.
    * MT1 = first "lower" master tree, whose blockchain timestamp is lower than the UPP one.
    * MT0 = similar to MT4, but the other way around
    * BCU1/2 = example of 2 blockchains transactions associated to the master tree 2. Correspond to the upper bound
    * There the anchoring time will be posterior than the created time of the UPP
    * BCL2/BCL3 = example of 2 blockchains transactions associated to the master tree 1. Correspond to part of the lower bound
    * BCL1 = lowest blockchain found, associated to MT0
    * There the anchoring time will be anterior than the created time of the UPP
    *
    *     BCL1         BCL2/BCL3                             BCU1/BCU2       BCU3
    *      |             |                                      |            |
    *     MT0---MT------MT1---MT----MT----MT2-----MT-----MT----MT3----MT----MT4
    *                                      |
    *                         FT----FT----FT
    *                         |
    *                        UPP
    *
    * @param id Hash of the vertex
    * @return 2 vertices List corresponding to the upper and lower path
    */
  def findUpperAndLower(id: String): (List[VertexStruct], List[VertexStruct]) = {

    val shortestPathRaw = shortestPathFromVertexToBlockchain(id)
    val shortestPath = PathHelper(shortestPathRaw)

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

    val firstUpperBlockchains: List[VertexStruct] = maybeLastMasterAndTime match {
      case Some((v, _)) =>
        getBlockchainsFromMasterVertex(v)
      case None =>
        Nil
    }

    val bcxNameSoFar = getBlockchainNamesFromBcx(firstUpperBlockchains).map(x => x._2)

    val upperThings: PathHelper = if (bcxNameSoFar.size >= numberDifferentAnchors) {
      logger.debug("UpperThings: already found enough blockchains")
      PathHelper(shortestPathRaw ++ firstUpperBlockchains)
    } else {
      maybeLastMasterAndTime match {
        case Some(masterAndTime) =>
          PathHelper(findOtherAnchors(
            pathAccu = shortestPathRaw,
            lastMt = masterAndTime._1,
            blockchainsFound = bcxNameSoFar,
            inDirection = true,
            numberDifferentAnchors = numberDifferentAnchors
          ))
        case None => PathHelper(Nil)
      }
    }

    val lowerPathHelper: Option[(List[VertexStruct], Long)] = {
      if (safeMode) {
        maybeLastMasterAndTime match {
          case Some((v, t)) => Option(outLT(v, t), t)
          case None => None
        }
      } else {
        val maybeFirstMasterAndTime: Option[(VertexStruct, Long)] = shortestPath.firstMasterOption.map(x => (x, headTimestamp.getOrElse(-1L)))
        maybeFirstMasterAndTime match {
          case Some((v, t)) => Option(outLT(v, t), t)
          case None => None
        }
      }
    }

    val lowerThings: List[VertexStruct] = lowerPathHelper match {
      case Some((ph, _)) =>
        val lowerBcs: List[VertexStruct] = PathHelper(ph).reversedHeadOption
          .map(x => getBlockchainsFromMasterVertex(x))
          .getOrElse(Nil)
        val bcxLowNameSoFar: List[(VertexStruct, String)] = getBlockchainNamesFromBcx(lowerBcs)
        if (bcxLowNameSoFar.map(x => x._2).size >= numberDifferentAnchors) {
          logger.debug("LowerThings: already found enough blockchains")
          ph ++ lowerBcs
        } else {
          val path = findOtherAnchors(
            pathAccu = lowerPathHelper.get._1 ++ lowerBcs,
            lastMt = lowerPathHelper.get._1.last,
            blockchainsFound = bcxLowNameSoFar.map(y => y._2),
            inDirection = false,
            numberDifferentAnchors = numberDifferentAnchors
          )
          path
        }
      case None =>
        Nil
    }
    (upperThings.path.distinct, lowerThings)

  }

  /**
    * Starting from the path passed in pathAccu, will try to return a path from the lastMt to new blockchains.
    * The list of blockchains already found (public chain names) is
    * stored in bcx. The boolean inDirection corresponds to which direction the algorithm will look for a new BC. The
    * algorithm will stop either:
    * - when the number of blockchains found is >= to numberDifferentAnchors.
    * - when the query has run for 1 second without finding new diffrent anchors.
    * @param pathAccu The previous path which will be updated with the new findings.
    * @param lastMt The last MT on which blockchains have been found.
    * @param blockchainsFound List of names of already found blockchains.
    * @param inDirection Boolean representing the direction on which to traverse the graph.
    *                    True => increasing time, False => decreasing time.
    * @param numberDifferentAnchors Int representing how many blockchains have to be found (including the ones already
    *                               defined in bcx) before stopping.
    * @return Path with blockchains.
    */
  def findOtherAnchors(pathAccu: List[VertexStruct], lastMt: VertexStruct, blockchainsFound: List[String], inDirection: Boolean, numberDifferentAnchors: Int): List[VertexStruct] = {

    val newPath: List[VertexStruct] = gremlin.g.V(lastMt.id)
      .nextBlockchainsFromMasterThatAreNotAlreadyFound(blockchainsFound, inDirection)
      .l()
      .map(v => VertexStruct.fromMap(v))

    val newBcx = newPath.filter(v => v.label == Values.PUBLIC_CHAIN_CATEGORY)

    val newBlockchainNames = getBlockchainNamesFromBcx(newBcx).map(_._2)

    val allBcxSoFar = blockchainsFound ++ newBlockchainNames

    val pathAccuUpdated = pathAccu ++ newPath.drop(1) //using drop(1) instead of tail in case newPath is empty

    if (allBcxSoFar.size >= numberDifferentAnchors) {
      pathAccuUpdated
    } else {
      if (newPath == Nil) {
        pathAccuUpdated
      } else {
        val lastMt = pathAccuUpdated.filter(p => p.label == Values.MASTER_TREE_CATEGORY).last
        findOtherAnchors(pathAccuUpdated, lastMt, allBcxSoFar, inDirection, numberDifferentAnchors)
      }
    }
  }

  def getBlockchainNamesFromBcx(blockchains: List[VertexStruct]): List[(VertexStruct, String)] = {
    blockchains.map(b => (b, b.properties.getOrElse(Values.PUBLIC_CHAIN_PROPERTY, "ERROR").asInstanceOf[String]))
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
      .shortestPathFromUppToBlockchain
      .l()
    shortestPath.map(v => VertexStruct.fromMap(v))
  }

  def findAnchorsWithPathAsVertices(id: String): Future[(List[VertexStruct], List[VertexStruct])] = {
    val (path, anchors) = findAnchorsWithPath(id)
    Future.successful(asVerticesDecorated(path ++ anchors))
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
