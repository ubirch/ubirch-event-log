package com.ubirch.discovery.process

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.discovery.models.Relation.Implicits._
import com.ubirch.discovery.models.{ Edge, Relation, Vertex }
import com.ubirch.discovery.util.DiscoveryJsonSupport
import com.ubirch.discovery.util.Exceptions.{ MasterTreeStrategyException, SlaveTreeStrategyException, UPPStrategyException, UnknownStrategyException }
import com.ubirch.models.{ EventLog, Values }
import com.ubirch.protocol.ProtocolMessage

import scala.util.{ Failure, Success, Try }

sealed trait RelationStrategy {
  def create(eventLog: EventLog): Seq[Relation]
}

object RelationStrategy {
  def create(eventLog: EventLog): Seq[Relation] = {
    val strategy = eventLog match {
      case el if el.category == Values.UPP_CATEGORY => UPPStrategy
      case el if el.category == Values.SLAVE_TREE_CATEGORY => SlaveTreeStrategy
      case el if el.category == Values.MASTER_TREE_CATEGORY => MasterTreeStrategy
      case el if el.lookupKeys.exists(_.category == Values.PUBLIC_CHAIN_CATEGORY) => PublicBlockchainStrategy
      case _ => UnknownStrategy
    }
    strategy.create(eventLog)
  }
}

case object UPPStrategy extends RelationStrategy with LazyLogging {
  /**
    * Create a list of CHAIN and UPP->DEVICE relation
    */
  def get(eventLog: EventLog): Seq[Relation] = {

    val ubirchPacket = DiscoveryJsonSupport.FromJson[ProtocolMessage](eventLog.event).get

    val signature = Option(ubirchPacket)
      .flatMap(x => Option(x.getSignature))
      .map(x => Try(org.bouncycastle.util.encoders.Base64.toBase64String(x)))
      .flatMap {
        case Success(value) if value.nonEmpty => Some(value)
        case Success(_) => None
        case Failure(e) =>
          logger.error("Error Parsing Into Event Log [Signature]: {}", e.getMessage)
          throw new Exception(s"Error parsing signature [${e.getMessage}] ")
      }.getOrElse(throw new Exception("No signature found"))

    val device = Option(ubirchPacket)
      .flatMap(x => Option(x).map(_.getUUID))
      .map(x => Try(x.toString))
      .flatMap {
        case Success(value) if value.nonEmpty => Some(value)
        case Success(_) => None
        case Failure(e) =>
          logger.error("Error Parsing Into Event Log [deviceId]: {}", e.getMessage)
          throw new Exception(s"Error parsing deviceId [${e.getMessage}] ")
      }.getOrElse(throw new Exception("No device found"))

    val maybeChain = Option(ubirchPacket)
      .flatMap(x => Option(x.getChain))
      .map(x => Try(org.bouncycastle.util.encoders.Base64.toBase64String(x)))
      .flatMap {
        case Success(value) if value.nonEmpty => Some(value)
        case Success(_) => None
        case Failure(e) =>
          logger.error("Error Parsing Into Event Log [Chain]: {}", e.getMessage)
          throw new Exception(s"Error parsing chain [${e.getMessage}] ")
      }

    //"service", "device-id", "upp", "chain"
    //logger.info("[event-log-trace] upp={} device={} chain={}", eventLog.id, device, maybeChain.getOrElse(""))
    val relation1 =
      Vertex(Values.UPP_CATEGORY)
        .addProperty(Values.HASH -> eventLog.id)
        .addProperty(Values.SIGNATURE -> signature)
        .addProperty(Values.TIMESTAMP -> eventLog.eventTime.getTime)
        .connectedTo(
          Vertex(Values.DEVICE_CATEGORY)
            .addProperty(Values.DEVICE_ID -> device)
        )
        .through(Edge(Values.UPP_CATEGORY + "->" + Values.DEVICE_CATEGORY).addProperty(Values.TIMESTAMP -> eventLog.eventTime.getTime))

    val maybeRelation2 = maybeChain.map { chain =>
      relation1
        .vFrom
        .connectedTo(
          Vertex(Values.UPP_CATEGORY)
            .addProperty(Values.SIGNATURE -> chain)
        )
        .through(Edge(Values.CHAIN_CATEGORY).addProperty(Values.TIMESTAMP -> eventLog.eventTime.getTime))

    }

    Seq(relation1) ++ maybeRelation2.toSeq
  }

  override def create(eventLog: EventLog): Seq[Relation] = {
    try {
      get(eventLog)
    } catch {
      case e: Exception =>
        logger.error("Relation Creation Error: ", e)
        throw UPPStrategyException("Relation Creation Error", e.getMessage, eventLog)

    }

  }
}

case object SlaveTreeStrategy extends RelationStrategy with LazyLogging {

  /**
    * Create a list of SLAVE_TREE->UPP relation
    */
  def uppRelations(eventLog: EventLog) = {

    def relation(hash: String, signature: String) = {

      //logger.info("[event-log-trace] upp={} foundation-tree={}", hash, eventLog.id)

      Vertex(Values.SLAVE_TREE_CATEGORY)
        .addProperty(Values.HASH -> eventLog.id)
        .addProperty(Values.TIMESTAMP -> eventLog.eventTime.getTime)
        .connectedTo(
          Vertex(Values.UPP_CATEGORY)
            .addProperty(Values.HASH -> hash)
            .addProperty(Values.SIGNATURE -> signature)
        ).through(Edge(Values.SLAVE_TREE_CATEGORY + "->" + Values.UPP_CATEGORY).addProperty(Values.TIMESTAMP -> eventLog.eventTime.getTime))

    }

    eventLog.lookupKeys
      .find(x => x.category == Values.SLAVE_TREE_CATEGORY && x.name == Values.SLAVE_TREE_ID)
      .map(_.value)
      .getOrElse(Nil)
      .map(x => relation(x.name, x.extra(Values.SIGNATURE)))
  }

  /**
    * Create a list of SLAVE_TREE->SLAVE_TREE relation
    */
  def linkRelations(eventLog: EventLog) = {

    def relation(hash: String) = {

      Vertex(Values.SLAVE_TREE_CATEGORY)
        .addProperty(Values.HASH -> eventLog.id)
        .addProperty(Values.TIMESTAMP -> eventLog.eventTime.getTime)
        .connectedTo(
          Vertex(Values.SLAVE_TREE_CATEGORY)
            .addProperty(Values.HASH -> hash)
        ).through(Edge(Values.SLAVE_TREE_CATEGORY + "->" + Values.SLAVE_TREE_CATEGORY).addProperty(Values.TIMESTAMP -> eventLog.eventTime.getTime))

    }

    eventLog.lookupKeys
      .find(x => x.category == Values.SLAVE_TREE_CATEGORY && x.name == Values.SLAVE_TREE_LINK_ID)
      .map(_.value)
      .getOrElse(Nil)
      .map(x => relation(x.name))
  }

  override def create(eventLog: EventLog): Seq[Relation] = {
    try {
      uppRelations(eventLog) ++ linkRelations(eventLog)
    } catch {
      case e: Exception =>
        logger.error("Relation Creation Error: ", e)
        throw SlaveTreeStrategyException("Relation Creation Error", e.getMessage, eventLog)
    }

  }
}

case object MasterTreeStrategy extends RelationStrategy with LazyLogging {

  /**
    * Create a list of MASTER_TREE->SLAVE_TREE relation
    */
  def treeRelations(eventLog: EventLog) = {

    def relation(hash: String) = {
      //logger.info("[event-log-trace] foundation-tree={} master-tree={}", hash, eventLog.id)

      Vertex(Values.MASTER_TREE_CATEGORY)
        .addProperty(Values.HASH -> eventLog.id)
        .addProperty(Values.TIMESTAMP -> eventLog.eventTime.getTime)
        .connectedTo(
          Vertex(Values.SLAVE_TREE_CATEGORY)
            .addProperty(Values.HASH -> hash)
        )
        .through(Edge(Values.MASTER_TREE_CATEGORY + "->" + Values.SLAVE_TREE_CATEGORY).addProperty(Values.TIMESTAMP -> eventLog.eventTime.getTime))

    }

    eventLog.lookupKeys
      .find(x => x.category == Values.MASTER_TREE_CATEGORY && x.name == Values.MASTER_TREE_ID)
      .map(_.value)
      .getOrElse(Nil)
      .map(x => relation(x.name))
  }

  /**
    * Create a list of MASTER_TREE->MASTER_TREE relation
    */
  def linkRelations(eventLog: EventLog) = {

    def relation(hash: String) = {

      //logger.info("[event-log-trace] master-tree-from={} master-tree-to={}", eventLog.id, hash)

      Vertex(Values.MASTER_TREE_CATEGORY)
        .addProperty(Values.HASH -> eventLog.id)
        .addProperty(Values.TIMESTAMP -> eventLog.eventTime.getTime)
        .connectedTo(
          Vertex(Values.MASTER_TREE_CATEGORY)
            .addProperty(Values.HASH -> hash)
        ).through(Edge(Values.MASTER_TREE_CATEGORY + "->" + Values.MASTER_TREE_CATEGORY).addProperty(Values.TIMESTAMP -> eventLog.eventTime.getTime))
    }

    eventLog.lookupKeys
      .find(x => x.category == Values.MASTER_TREE_CATEGORY && x.name == Values.MASTER_TREE_LINK_ID)
      .map(_.value)
      .getOrElse(Nil)
      .map(x => relation(x.name))
  }

  override def create(eventLog: EventLog): Seq[Relation] = {
    try {
      treeRelations(eventLog) ++ linkRelations(eventLog)
    } catch {
      case e: Exception =>
        logger.error("Relation Creation Error: ", e)
        throw MasterTreeStrategyException("Relation Creation Error", e.getMessage, eventLog)
    }
  }
}

case object PublicBlockchainStrategy extends RelationStrategy with LazyLogging {
  /**
    * Create a list of PUBLIC_CHAIN->MASTER_TREE relation
    */
  def relation(eventLog: EventLog, hash: String) = {
    //logger.info("[event-log-trace] blockchain={} master-tree={} blockchain-name={}", eventLog.id, hash, eventLog.category)

    Vertex(Values.PUBLIC_CHAIN_CATEGORY)
      .addProperty(Values.HASH -> eventLog.id)
      .addProperty(Values.PUBLIC_CHAIN_CATEGORY -> eventLog.category)
      .addProperty(Values.TIMESTAMP -> eventLog.eventTime.getTime)
      .connectedTo(
        Vertex(Values.MASTER_TREE_CATEGORY)
          .addProperty(Values.HASH -> hash)
      )
      .through(Edge(Values.PUBLIC_CHAIN_CATEGORY + "->" + Values.MASTER_TREE_CATEGORY).addProperty(Values.TIMESTAMP -> eventLog.eventTime.getTime))

  }

  def get(eventLog: EventLog) = eventLog.lookupKeys
    .find(_.category == Values.PUBLIC_CHAIN_CATEGORY)
    .map(_.value)
    .getOrElse(Nil)
    .map(x => relation(eventLog, x.name))

  override def create(eventLog: EventLog): Seq[Relation] = {
    try {
      get(eventLog)
    } catch {
      case e: Exception =>
        logger.error("Relation Creation Error: ", e)
        throw MasterTreeStrategyException("Relation Creation Error", e.getMessage, eventLog)
    }
  }
}

case object UnknownStrategy extends RelationStrategy {
  override def create(eventLog: EventLog): Seq[Relation] =
    throw UnknownStrategyException("UnknownStrategyException", "There's no strategy for category " + eventLog.category, eventLog)
}

