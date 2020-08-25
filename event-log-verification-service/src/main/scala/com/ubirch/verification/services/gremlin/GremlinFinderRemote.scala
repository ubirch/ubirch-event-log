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
  * This finder is to be used when connecting a remote gremlin server
  */
@Singleton
class GremlinFinderRemote @Inject() (gremlin: Gremlin, config: Config)(implicit ec: ExecutionContext) extends GremlinFinder with LazyLogging {

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
      (shortest, upper, lowerPath, lower) <- findUpperAndLower(id)
    } yield {
      val (completePathShortest, completeBlockchainsUpper) = asVerticesDecorated(shortest, upper)
      val (completePathLower, completeBlockchainsLower) = asVerticesDecorated(lowerPath, lower)
      (completePathShortest, completeBlockchainsUpper, completePathLower, completeBlockchainsLower)
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

    val upperBlockchains: Future[List[VertexStruct]] = maybeLastMasterAndTime.flatMap {
      case Some((v, _)) =>
        getBlockchainsFromMasterVertex(v)
      case None =>
        Future.successful(Nil)
    }

    val lowerPathHelper: Future[Option[(PathHelper, Long)]] = {
      if (safeMode) {
        maybeLastMasterAndTime.flatMap {
          case Some((v, t)) => outLT(v, t).map(x => Option(PathHelper(x), t))
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
          case Some((v, t)) => outLT(v, t).map(x => Option(PathHelper(x), t))
          case None => Future.successful(None)
        }
      }
    }

    val lowerBlockchains: Future[List[VertexStruct]] = lowerPathHelper.flatMap {
      case Some((ph, _)) =>
        ph.reversedHeadOption
          .map(x => getBlockchainsFromMasterVertex(x))
          .getOrElse(Future.successful(Nil))
      case None =>
        Future.successful(Nil)
    }

    for {
      path <- futureShortestPath.map(_.reversedTailReversed)
      up <- upperBlockchains
      lp <- lowerPathHelper
      lw <- lowerBlockchains
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
      .shortestPathUppBlockchain
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
      .blockchainsFromMasterVertex
      .promise()
    for {
      blockchains <- futureBlockchains
    } yield {
      blockchains.map(b => VertexStruct.fromMap(b))
    }
  }

}
