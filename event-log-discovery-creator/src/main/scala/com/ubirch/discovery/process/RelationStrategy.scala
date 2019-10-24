package com.ubirch.discovery.process

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.discovery.models.{ Edge, Relation, Vertex }
import com.ubirch.discovery.services.metrics.DefaultDeviceCounter
import com.ubirch.discovery.util.DiscoveryJsonSupport
import com.ubirch.discovery.util.Exceptions.{ MasterTreeStrategyException, SlaveTreeStrategyException, UPPStrategyException, UnknownStrategyException }
import com.ubirch.models.{ EventLog, Values }
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.services.metrics.Counter
import javax.inject._
import Relation.Implicits._

import scala.util.{ Failure, Success, Try }

sealed trait RelationStrategy {
  def eventLog: EventLog
  def create: Seq[Relation]
}

@Singleton
class RelationStrategyImpl @Inject() (@Named(DefaultDeviceCounter.name) deviceCounter: Counter) {

  def getStrategy(eventLog: EventLog): RelationStrategy = {
    eventLog match {
      case el if el.category == Values.UPP_CATEGORY => UPPStrategy(el, deviceCounter)
      case el if el.category == Values.SLAVE_TREE_CATEGORY => SlaveTreeStrategy(el)
      case el if el.category == Values.MASTER_TREE_CATEGORY => MasterTreeStrategy(el)
      case el if el.lookupKeys.exists(_.category == Values.PUBLIC_CHAIN_CATEGORY) => PublicBlockchainStrategy(el)
      case el => UnknownStrategy(el)
    }
  }

}

case class UPPStrategy(eventLog: EventLog, deviceCounter: Counter) extends RelationStrategy with LazyLogging {

  def get = {

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
    deviceCounter.counter.labels("event_log_trace").inc()
    logger.info("[event-log-trace] upp={} device={} chain={}", eventLog.id, device, maybeChain.getOrElse(""))

    val relation1 =
      Vertex(Values.UPP_CATEGORY)
        .addProperty(Values.HASH -> eventLog.id)
        .addProperty(Values.SIGNATURE -> signature)
        .addProperty(Values.TYPE -> Values.UPP_CATEGORY)
        .connectedTo(
          Vertex(Values.DEVICE_CATEGORY)
            .addProperty(Values.DEVICE_ID -> device)
            .addProperty(Values.TYPE -> Values.DEVICE_CATEGORY)
        )
        .through(Edge.simple(Values.DEVICE_CATEGORY))

    val maybeRelation2 = maybeChain.map { chain =>
      relation1
        .vFrom
        .connectedTo(
          Vertex(Values.UPP_CATEGORY)
            .addProperty(Values.SIGNATURE -> chain)
            .addProperty(Values.TYPE -> Values.UPP_CATEGORY)
        )
        .through(Edge.simple(Values.CHAIN_CATEGORY))

    }

    Seq(relation1) ++ maybeRelation2.toSeq
  }

  override def create: Seq[Relation] = {
    try {
      get
    } catch {
      case e: Exception =>
        logger.error("Relation Creation Error: ", e)
        throw UPPStrategyException("Relation Creation Error", e.getMessage, eventLog)

    }

  }
}

case class SlaveTreeStrategy(eventLog: EventLog) extends RelationStrategy with LazyLogging {

  def uppRelations = {

    def relation(hash: String, signature: String) = {

      logger.info("[event-log-trace] upp={} foundation-tree={}", hash, eventLog.id)

      Vertex(Values.SLAVE_TREE_CATEGORY)
        .addProperty(Values.HASH -> eventLog.id)
        .addProperty(Values.TYPE -> Values.SLAVE_TREE_CATEGORY)
        .connectedTo(
          Vertex(Values.UPP_CATEGORY)
            .addProperty(Values.HASH -> hash)
            .addProperty(Values.SIGNATURE -> signature)
            .addProperty(Values.TYPE -> Values.UPP_CATEGORY)
        ).through(Edge(Values.SLAVE_TREE_CATEGORY))

    }

    eventLog.lookupKeys
      .find(x => x.category == Values.SLAVE_TREE_CATEGORY && x.name == Values.SLAVE_TREE_ID)
      .map(_.value)
      .getOrElse(Nil)
      .map(x => relation(x.name, x.extra(Values.SIGNATURE)))
  }

  def linkRelations = {

    def relation(hash: String) = {
      logger.info("[event-log-trace] foundation-tree-from={} foundation-tree-to={}", eventLog.id, hash)

      Vertex(Values.SLAVE_TREE_CATEGORY)
        .addProperty(Values.HASH -> eventLog.id)
        .addProperty(Values.TYPE -> Values.SLAVE_TREE_CATEGORY)
        .connectedTo(
          Vertex(Values.SLAVE_TREE_CATEGORY)
            .addProperty(Values.HASH -> hash)
            .addProperty(Values.TYPE -> Values.SLAVE_TREE_CATEGORY)
        ).through(Edge(Values.SLAVE_TREE_CATEGORY))

    }

    eventLog.lookupKeys
      .find(x => x.category == Values.SLAVE_TREE_CATEGORY && x.name == Values.SLAVE_TREE_LINK_ID)
      .map(_.value)
      .getOrElse(Nil)
      .map(x => relation(x.name))
  }

  def upgradeRelations = {

    def relation(hash: String) =
      Vertex(Values.SLAVE_TREE_CATEGORY)
        .addProperty(Values.HASH -> eventLog.id)
        .addProperty(Values.TYPE -> Values.SLAVE_TREE_CATEGORY)
        .connectedTo(
          Vertex(Values.MASTER_TREE_CATEGORY)
            .addProperty(Values.HASH -> hash)
            .addProperty(Values.TYPE -> Values.MASTER_TREE_CATEGORY)
        )
        .through(Edge(Values.SLAVE_TREE_CATEGORY + "_UPGRADE"))

    eventLog.lookupKeys
      .find(x => x.category == Values.SLAVE_TREE_CATEGORY + "_UPGRADE" && x.name == Values.SLAVE_TREE_UPGRADE_ID)
      .map(_.value)
      .getOrElse(Nil)
      .map(x => relation(x.name))
  }

  override def create: Seq[Relation] = {
    try {
      uppRelations ++ linkRelations ++ upgradeRelations
    } catch {
      case e: Exception =>
        logger.error("Relation Creation Error: ", e)
        throw SlaveTreeStrategyException("Relation Creation Error", e.getMessage, eventLog)
    }

  }
}

case class MasterTreeStrategy(eventLog: EventLog) extends RelationStrategy with LazyLogging {

  def treeRelations = {

    def relation(hash: String) = {
      logger.info("[event-log-trace] foundation-tree={} master-tree={}", hash, eventLog.id)

      Vertex(Values.MASTER_TREE_CATEGORY)
        .addProperty(Values.HASH -> eventLog.id)
        .addProperty(Values.TYPE -> Values.MASTER_TREE_CATEGORY)
        .connectedTo(
          Vertex(Values.SLAVE_TREE_CATEGORY)
            .addProperty(Values.HASH -> hash)
            .addProperty(Values.TYPE -> Values.SLAVE_TREE_CATEGORY)
        )
        .through(Edge(Values.MASTER_TREE_CATEGORY))

    }

    eventLog.lookupKeys
      .find(x => x.category == Values.MASTER_TREE_CATEGORY && x.name == Values.MASTER_TREE_ID)
      .map(_.value)
      .getOrElse(Nil)
      .map(x => relation(x.name))
  }

  def linkRelations = {

    def relation(hash: String) = {

      logger.info("[event-log-trace] master-tree-from={} master-tree-to={}", eventLog.id, hash)

      Vertex(Values.MASTER_TREE_CATEGORY)
        .addProperty(Values.HASH -> eventLog.id)
        .addProperty(Values.TYPE -> Values.MASTER_TREE_CATEGORY)
        .connectedTo(
          Vertex(Values.MASTER_TREE_CATEGORY)
            .addProperty(Values.HASH -> hash)
            .addProperty(Values.TYPE -> Values.MASTER_TREE_CATEGORY)
        ).through(Edge(Values.MASTER_TREE_CATEGORY))

    }

    eventLog.lookupKeys
      .find(x => x.category == Values.MASTER_TREE_CATEGORY && x.name == Values.MASTER_TREE_LINK_ID)
      .map(_.value)
      .getOrElse(Nil)
      .map(x => relation(x.name))
  }

  def upgradeRelations = {

    def relation(hash: String) =
      Vertex(Values.MASTER_TREE_CATEGORY)
        .addProperty(Values.HASH -> eventLog.id)
        .addProperty(Values.TYPE -> Values.MASTER_TREE_CATEGORY)
        .connectedTo(
          Vertex(Values.MASTER_TREE_CATEGORY)
            .addProperty(Values.HASH -> hash)
            .addProperty(Values.TYPE -> Values.MASTER_TREE_CATEGORY)
        )
        .through(Edge(Values.MASTER_TREE_CATEGORY))

    eventLog.lookupKeys
      .find(x => x.category == Values.MASTER_TREE_CATEGORY + "_UPGRADE" && x.name == Values.MASTER_TREE_UPGRADE_ID)
      .map(_.value)
      .getOrElse(Nil)
      .map(x => relation(x.name))
  }

  override def create: Seq[Relation] = {
    try {
      treeRelations ++ linkRelations ++ upgradeRelations
    } catch {
      case e: Exception =>
        logger.error("Relation Creation Error: ", e)
        throw MasterTreeStrategyException("Relation Creation Error", e.getMessage, eventLog)
    }
  }
}

case class PublicBlockchainStrategy(eventLog: EventLog) extends RelationStrategy with LazyLogging {
  def relation(hash: String) = {
    logger.info("[event-log-trace] blockchain={} master-tree={} blockchain-name={}", eventLog.id, hash, eventLog.category)

    Vertex(Values.PUBLIC_CHAIN_CATEGORY)
      .addProperty(Values.HASH -> eventLog.id)
      .addProperty(Values.TYPE -> Values.PUBLIC_CHAIN_CATEGORY)
      .addProperty(Values.PUBLIC_CHAIN_CATEGORY -> eventLog.category)
      .connectedTo(
        Vertex(Values.MASTER_TREE_CATEGORY)
          .addProperty(Values.HASH -> hash)
          .addProperty(Values.TYPE -> Values.MASTER_TREE_CATEGORY)
      )
      .through(Edge(Values.PUBLIC_CHAIN_CATEGORY))

  }

  def get = eventLog.lookupKeys
    .find(_.category == Values.PUBLIC_CHAIN_CATEGORY)
    .map(_.value)
    .getOrElse(Nil)
    .map(x => relation(x.name))

  override def create: Seq[Relation] = {
    try {
      get
    } catch {
      case e: Exception =>
        logger.error("Relation Creation Error: ", e)
        throw MasterTreeStrategyException("Relation Creation Error", e.getMessage, eventLog)
    }
  }
}

case class UnknownStrategy(eventLog: EventLog) extends RelationStrategy {
  override def create: Seq[Relation] =
    throw UnknownStrategyException("UnknownStrategyException", "There's no strategy for category " + eventLog.category, eventLog)
}

