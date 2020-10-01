package com.ubirch.verification.services.janus

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.Values
import com.ubirch.verification.models.VertexStruct
import com.ubirch.verification.services.janus.GremlinFinder._
import javax.inject._

import scala.concurrent.{ ExecutionContext, Future }

/**
  * This finder is to be used when connecting a remote gremlin server
  */
@Singleton
class GremlinFinderRemote @Inject() (gremlin: Gremlin, config: Config)(implicit ec: ExecutionContext) extends GremlinFinder with LazyLogging {

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
  def findUpperAndLowerAsVertices(id: String): Future[(List[VertexStruct], List[VertexStruct], List[VertexStruct], List[VertexStruct])] =
    for {
      (upper, lower) <- findUpperAndLower(id)
    } yield {
      val (completePathShortest, completeBlockchainsUpper) = asVerticesDecorated(upper)
      val (completePathLower, completeBlockchainsLower) = asVerticesDecorated(lower)
      (completePathShortest, completeBlockchainsUpper, completePathLower, completeBlockchainsLower)
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
    * @return A Future of 2 vertices List corresponding to the upper and lower path
    */
  def findUpperAndLower(id: String): Future[(List[VertexStruct], List[VertexStruct])] = {

    val futureShortestPathRaw = shortestPathFromVertexToBlockchain(id)
    val futureShortestPath: Future[PathHelper] = futureShortestPathRaw.map(PathHelper)

    val headTimestamp: Future[Option[Long]] = futureShortestPath.map(_.headOption).flatMap {
      case Some(v) =>
        getTimestampFromVertexAsDate(v) match {
          case Some(value) => Future.successful(Some(value.getTime))
          case None => Future.successful(getTimestampFromVertexAsLong(v))
        }
      case None => Future.successful(None)
    }

    val futureMaybeLastMasterAndTime: Future[Option[(VertexStruct, Long)]] = for {
      time <- headTimestamp
      master <- futureShortestPath.map(_.reversedTailHeadOption)
    } yield {
      master.map(x => (x, time.getOrElse(-1L)))
    }

    val futureFirstUpperBlockchains: Future[List[VertexStruct]] = futureMaybeLastMasterAndTime.flatMap {
      case Some((v, _)) =>
        getBlockchainsFromMasterVertex(v)
      case None =>
        Future.successful(Nil)
    }

    val futureBcxNamesSoFar = getBlockchainNamesFromFutureBcx(futureFirstUpperBlockchains).map(x => x.map(q => q._2))

    val unflattenedUpperThings: Future[Future[PathHelper]] = for {
      bcxNamesSoFar <- futureBcxNamesSoFar
      maybeLastMasterAndTime <- futureMaybeLastMasterAndTime
    } yield {
      if (bcxNamesSoFar.size >= numberDifferentAnchors) {
        logger.debug("UpperThings: already found enough blockchains")
        for {
          shortestPathRaw <- futureShortestPathRaw
          firstUpperBlockchains <- futureFirstUpperBlockchains
        } yield {
          PathHelper(shortestPathRaw ++ firstUpperBlockchains)
        }
      } else {
        maybeLastMasterAndTime match {
          case Some(masterAndTime) =>
            findOtherAnchors(
              futurePathAccu = futureShortestPathRaw,
              futureLastMt = Future.successful(masterAndTime._1),
              futureBlockchainsFound = futureBcxNamesSoFar,
              inDirection = true,
              numberDifferentAnchors = numberDifferentAnchors
            ).map(PathHelper)
          case None =>
            Future.successful(PathHelper(Nil))
        }
      }
    }

    val futureUpperThings: Future[PathHelper] = unflattenedUpperThings.flatten

    val futureLowerPathHelper: Future[Option[(List[VertexStruct], Long)]] = {
      if (safeMode) {
        futureMaybeLastMasterAndTime.flatMap {
          case Some((v, t)) => outLT(v, t).map(x => Option(x, t))
          case None => Future.successful(None)
        }
      } else {
        val maybeFirstMasterAndTime: Future[Option[(VertexStruct, Long)]] = for {
          time <- headTimestamp
          master <- futureShortestPath.map(_.firstMasterOption)
        } yield {
          master.map(x => (x, time.getOrElse(-1L)))
        }
        maybeFirstMasterAndTime.flatMap {
          case Some((v, t)) => outLT(v, t).map(x => Option(x, t))
          case None => Future.successful(None)
        }
      }
    }

    val lowerThings: Future[List[VertexStruct]] = futureLowerPathHelper.flatMap {
      case Some((ph, _)) =>
        val fLowerBcs = PathHelper(ph).reversedHeadOption
          .map(x => getBlockchainsFromMasterVertex(x))
          .getOrElse(Future.successful(Nil))
        val futureBcxLowNameSoFar = getBlockchainNamesFromFutureBcx(fLowerBcs)
        futureBcxLowNameSoFar.flatMap { bcxLowNameSoFar =>
          if (bcxLowNameSoFar.map(x => x._2).size >= numberDifferentAnchors) {
            Future.successful(ph)
          } else {
            //val a = lowerPathHelper.map(x => x.get._1)
            val lowPathForNow = for {
              lowerBcs <- fLowerBcs
              lowPath <- futureLowerPathHelper.map(x => x.get._1)
            } yield {
              lowPath ++ lowerBcs
            }
            val path = findOtherAnchors(lowPathForNow, futureLowerPathHelper.map(x => x.get._1.last), futureBcxLowNameSoFar.map(x => x.map(y => y._2)), inDirection = false, numberDifferentAnchors)
            path
          }
        }
      case None =>
        Future.successful(Nil)
    }

    //val a = lowerThings.map(_.unzip match { case (x, y) => (x.flatten, y.flatten) })

    for {
      upperPath <- futureUpperThings
      lowerPath <- lowerThings
    } yield {
      (upperPath.path.distinct, lowerPath)
    }
  }

  /**
    * Starting from the path passed in pathAccu, will try to return a path from the lastMt to new blockchains.
    * The list of blockchains already found (public chain names) is
    * stored in bcx. The boolean inDirection corresponds to which direction the algorithm will look for a new BC. The
    * algorithm will stop either:
    * - when the number of blockchains found is >= to numberDifferentAnchors.
    * - when the query has run for 1 second without finding new diffrent anchors.
    * @param futurePathAccu The previous path which will be updated with the new findings.
    * @param futureLastMt The last MT on which blockchains have been found.
    * @param futureBlockchainsFound List of names of already found blockchains.
    * @param inDirection Boolean representing the direction on which to traverse the graph.
    *                    True => increasing time, False => decreasing time.
    * @param numberDifferentAnchors Int representing how many blockchains have to be found (including the ones already
    *                               defined in bcx) before stopping.
    * @return Path with blockchains.
    */
  def findOtherAnchors(futurePathAccu: Future[List[VertexStruct]], futureLastMt: Future[VertexStruct], futureBlockchainsFound: Future[List[String]], inDirection: Boolean, numberDifferentAnchors: Int): Future[List[VertexStruct]] = {

    val futureNewPath = for {
      alreadyFoundBcx <- futureBlockchainsFound
      lastMt <- futureLastMt
      path <- try {
        gremlin.g.V(lastMt.id)
          .nextBlockchainsFromMasterThatAreNotAlreadyFound(alreadyFoundBcx, inDirection)
          .promise()
          .map(lv => lv.map(v => VertexStruct.fromMap(v)))
      } catch {
        case e: Throwable =>
          logger.warn("Error in nextBlockchainsFromMasterThatAreNotAlreadyFound", e)
          Future.successful(Nil) // this catch is here in case an error is thrown by nextBlockchainsFromMasterThatAreNotAlreadyFound, meaning that the algorithm reached the end of the graph
      }
    } yield {
      path
    }

    val futureNewBcx = futureNewPath.map(_.filter(v => v.label == Values.PUBLIC_CHAIN_CATEGORY))

    val futureNewBlockchainNames = getBlockchainNamesFromFutureBcx(futureNewBcx).map(x => x.map(_._2))

    val futureAllBcxSoFar: Future[List[String]] = for {
      bcx <- futureBlockchainsFound
      newBlockchainNames <- futureNewBlockchainNames
    } yield {
      bcx ++ newBlockchainNames
    }

    val futurePathAccuUpdated = for {
      pathAccu <- futurePathAccu
      newPath <- futureNewPath
    } yield {
      pathAccu ++ newPath.drop(1) //using drop(1) instead of tail in case newPath is empty
    }

    val a = for {
      newPath <- futureNewPath
      allBcxSoFar <- futureAllBcxSoFar
    } yield {
      if (allBcxSoFar.size >= numberDifferentAnchors) {
        futurePathAccuUpdated
      } else {
        if (newPath == Nil) {
          futurePathAccuUpdated
        } else {
          val futureLastMt = for {
            path <- futurePathAccuUpdated
          } yield {
            path.filter(p => p.label == Values.MASTER_TREE_CATEGORY).last
          }
          findOtherAnchors(futurePathAccuUpdated, futureLastMt, futureAllBcxSoFar, inDirection, numberDifferentAnchors)
        }
      }
    }
    a.flatten
  }

  def getBlockchainNamesFromFutureBcx(futureBlockchains: Future[List[VertexStruct]]): Future[List[(VertexStruct, String)]] = {
    for {
      blockchains <- futureBlockchains
    } yield {
      blockchains.map(b => (b, b.properties.getOrElse(Values.PUBLIC_CHAIN_PROPERTY, "ERROR").asInstanceOf[String]))
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
      .simpleFindReturnElement(matchProperty.toLowerCase(), value, returnProperty.toLowerCase())
      .promise()

  /**
    * This function finds a master tree (connected to blockchains) whose date is inferior to the one passed as the argument
    * The starting point is the master that is passed as a parameter.
    * @param master Starting point of the search.
    * @param time Upper bound time of the queried master tree.
    * @return The path between the starting point and the master tree.
    */
  def outLT(master: VertexStruct, time: Long): Future[List[VertexStruct]] = {
    val res = gremlin.g.V(master.id)
      .toPreviousMasterWithBlockchain(time)
      .promise()
    for {
      vertices <- res
    } yield {
      vertices.map(v => VertexStruct.fromMap(v))
    }
  }

  def shortestPathFromVertexToBlockchain(hash: String): Future[List[VertexStruct]] = shortestPathUppBlockchain(Values.HASH, hash)

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
    val shortestPath = gremlin.g.V()
      .findVertex(property.toLowerCase(), value)
      .shortestPathToLabel(untilLabel)
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
  def shortestPathUppBlockchain(property: String, value: String): Future[List[VertexStruct]] = {
    //val x = StepLabel[java.util.Set[Vertex]]("x")
    //g.V().has("hash", hash).store("x").repeat(__.in().where(without("x")).aggregate("x")).until(hasLabel("PUBLIC_CHAIN")).limit(1).path().profile()
    val shortestPath = gremlin.g.V()
      .findVertex(property.toLowerCase(), value)
      .shortestPathFromUppToBlockchain
      .promise()
    for {
      sp <- shortestPath
    } yield {
      sp.map(v => {
        VertexStruct.fromMap(v)
      })
    }
  }

  def findAnchorsWithPathAsVertices(id: String): Future[(List[VertexStruct], List[VertexStruct])] =
    for {
      (path, anchors) <- findAnchorsWithPath(id)
    } yield {
      asVerticesDecorated(path ++ anchors)
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
      .blockchainsFromMasterVertex
      .promise()
    for {
      blockchains <- futureBlockchains
    } yield {
      blockchains.map(b => VertexStruct.fromMap(b))
    }
  }

}
