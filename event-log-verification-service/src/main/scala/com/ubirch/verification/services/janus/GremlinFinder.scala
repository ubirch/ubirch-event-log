package com.ubirch.verification.services.janus

import java.util
import java.util.Date

import com.ubirch.models.Values
import com.ubirch.util.TimeHelper
import com.ubirch.verification.models.VertexStruct
import gremlin.scala.{ GremlinScala, Key, P, Vertex }
import gremlin.scala.GremlinScala.Aux
import shapeless.HNil

import scala.concurrent.Future

trait GremlinFinder {

  /**
    * Find the upper and lower bound blockchains associated to the given vertex and decorates the vertices in the path.
    *
    * @param id Id of the vertex
    * @return
    */
  def findUpperAndLowerAsVertices(id: String): Future[(List[VertexStruct], List[VertexStruct], List[VertexStruct], List[VertexStruct])]

  /**
    * Look for a vertex having the matchProperty:value and return the value of his returnProperty
    *
    * @param matchProperty  Key of the lookup property
    * @param value          Value of the lookup property
    * @param returnProperty Key of the desired property
    * @return Value of the desired property
    */
  def simpleFind(matchProperty: String, value: String, returnProperty: String): Future[List[String]]

  def findAnchorsWithPathAsVertices(id: String): Future[(List[VertexStruct], List[VertexStruct])]

}

/**
  * Object containing helper or general methods used by the gremlin finder. Also contains the RichTraversal implicit class
  * that creates the traversal needed to find things.
  */
object GremlinFinder {

  def asVertices(path: List[VertexStruct]): (List[VertexStruct], List[VertexStruct]) = {

    def withPrevious(hashes: List[Any]) = Map(Values.PREV_HASH -> hashes.mkString(","))
    def withNext(hashes: List[Any]) = Map(Values.NEXT_HASH -> hashes.mkString(","))

    def getPreviousHash(processed: List[VertexStruct]) = processed.lastOption.map(_.properties)
      .flatMap(_.get(Values.HASH))
      .map(x => withPrevious(List(x)))
      .getOrElse(withPrevious(List("")))

    def getNextHash(nextVertices: List[VertexStruct]) = nextVertices.headOption
      .map(_.properties)
      .flatMap(_.get(Values.HASH))
      .map(x => withNext(List(x)))
      .getOrElse(withNext(List("")))

    def process(accu: List[VertexStruct], processed: List[VertexStruct], lastLastMtHash: Option[String]): List[VertexStruct] = {
      accu match {
        case Nil => processed
        case ::(current, nextVertices) =>
          current.label match {
            case Values.MASTER_TREE_CATEGORY =>
              val currentHash = current.properties.getOrElse(Values.HASH, "").asInstanceOf[String]
              val previousHash = lastLastMtHash match {
                case Some(hash) => withPrevious(List(hash))
                case None => getPreviousHash(processed)
              }
              val nextHash = nextVertices match {
                case Nil => getNextHash(nextVertices)
                case x =>
                  if (x.head.label == Values.PUBLIC_CHAIN_CATEGORY) {
                    def getGroupBc(list: List[VertexStruct], accuHashes: List[String]): List[String] = {
                      list match {
                        case Nil => accuHashes
                        case ::(head, tl) => if (head.label == Values.PUBLIC_CHAIN_CATEGORY) {
                          getGroupBc(tl, accuHashes :+ head.properties.getOrElse(Values.HASH, "").asInstanceOf[String])
                        } else {
                          accuHashes
                        }
                      }
                    }
                    withNext(getGroupBc(x, Nil))
                  } else {
                    getNextHash(nextVertices)
                  }
              }
              val processedHash = current.addProperties(previousHash).addProperties(nextHash)
              process(nextVertices, processed :+ processedHash, Some(currentHash))
            case Values.PUBLIC_CHAIN_CATEGORY =>
              val previousHash = lastLastMtHash.getOrElse("ERROR")
              val processedHash = current.addProperties(withPrevious(List(previousHash)))
              process(nextVertices, processed :+ processedHash, lastLastMtHash)
            case _ =>
              val previousHash = getPreviousHash(processed)
              val nextHash = getNextHash(nextVertices)
              val processedHash = current.addProperties(previousHash).addProperties(nextHash)
              process(nextVertices, processed :+ processedHash, lastLastMtHash)
          }
      }
    }
    val res = process(path, Nil, None)
    val blockchains = res.filter(p => p.label == Values.PUBLIC_CHAIN_CATEGORY)
    val rest = res.filterNot(p => p.label == Values.PUBLIC_CHAIN_CATEGORY)
    (rest, blockchains)
  }

  def asVerticesDecorated(path: List[VertexStruct]): (List[VertexStruct], List[VertexStruct]) = {
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

    asVertices(path) match {
      case (p, a) =>
        val _path = p.map(x =>
          x.map(Values.TIMESTAMP)(parseTimestamp)
            .addLabelWhen(Values.FOUNDATION_TREE_CATEGORY)(Values.SLAVE_TREE_CATEGORY))

        val _anchors = a.map(_.map(Values.TIMESTAMP)(parseTimestamp))

        (_path, _anchors)
    }
  }

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

  implicit class RichTraversal(val previousConstructor: GremlinScala.Aux[Vertex, HNil]) extends AnyVal {

    /**
      * Creates a gremlin traversal starting from previousConstructor and returning all the
      * properties (the element map) of the public chains in direct connection (in) to the given vertex
      */
    def blockchainsFromMasterVertex: Aux[util.Map[AnyRef, AnyRef], HNil] = previousConstructor.in()
      .hasLabel(Values.PUBLIC_CHAIN_CATEGORY)
      .elementMap

    /**
      * Creates a traversal from the previousTraversal to a new blockchain whose name is not in the alreadyFoundBlockchains list.
      * @param alreadyFoundBlockchains List of blockchain names that have already been found.
      * @param inDirection Specify on which direction the graph will be traversed. True = in, false = out.
      * @return The updated traversal.
      */
    def nextBlockchainsFromMasterThatAreNotAlreadyFound(alreadyFoundBlockchains: List[String], inDirection: Boolean): Aux[util.Map[AnyRef, AnyRef], HNil] = {

      previousConstructor.repeat(
        _.inOrOut(
          inDirection,
          Values.MASTER_TREE_CATEGORY + "->" + Values.MASTER_TREE_CATEGORY
        )
          .simplePath()
      )
        .until(
          _.in(Values.PUBLIC_CHAIN_CATEGORY + "->" + Values.MASTER_TREE_CATEGORY).addHasNotSteps(alreadyFoundBlockchains)
        )
        .timeLimit(1000L) // 1 second time limit
        .limit(1)
        .in(Values.PUBLIC_CHAIN_CATEGORY + "->" + Values.MASTER_TREE_CATEGORY).addHasNotSteps(alreadyFoundBlockchains)
        .path()
        .unfold[Vertex]()
        .elementMap
    }

    protected def inOrOut(upDirection: Boolean, value: String): Aux[Vertex, HNil] = if (upDirection) previousConstructor.in(value) else previousConstructor.out(value)

    protected def addHasNotSteps(alreadyFoundBlockchains: List[String], accu: GremlinScala.Aux[Vertex, HNil] = previousConstructor): GremlinScala.Aux[Vertex, HNil] = {

      val publicChainKey = Key[String](Values.PUBLIC_CHAIN_PROPERTY)

      alreadyFoundBlockchains match {
        case Nil => accu
        case ::(head, tl) =>
          addHasNotSteps(tl, accu.hasNot(publicChainKey, head))
      }
    }

    /**
      * Find ONE upp that has the given property and associated value.
      * @param property The property desired.
      * @param value    Value of the property. Only works if the value is a string.
      */
    def findVertex(property: String, value: String) = previousConstructor.has(Key[String](property), value)

    /**
      * Will create a traversal that returns the shortest path decorated with the element map of the vertices of the path
      * between the vertex selected on the previousConstructor and the first vertex having the untilLabel. Will only look
      * on the IN direction.
      * @param untilLabel Label on which to stop.
      */
    def shortestPathToLabel(untilLabel: String) = previousConstructor.repeat(_.in(
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

    /**
      * Optimized shortest path between a UPP and a blockchain. Still in the IN direction
      */
    def shortestPathFromUppToBlockchain: Aux[util.Map[AnyRef, AnyRef], HNil] = previousConstructor
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

    /**
      * Creates a traversal that will return the enriched path (vertices with their elementMap) from the vertex selected
      * in the previousConstructor to a master tree (in the OUT direction) who has at least one blockchain whose
      * timestamp is inferior to the one given in time
      * @param time Upper bound time of the queried master tree.
      */
    def toPreviousMasterWithBlockchain(time: Long): Aux[util.Map[AnyRef, AnyRef], HNil] = {
      val timestamp = Key[Date](Values.TIMESTAMP)
      val date = new Date(time)
      previousConstructor
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
    }

    def simpleFindReturnElement(matchProperty: String, value: String, returnProperty: String): Aux[String, HNil] =
      previousConstructor
        .findVertex(matchProperty, value)
        .value(Key[String](returnProperty))
  }

}
