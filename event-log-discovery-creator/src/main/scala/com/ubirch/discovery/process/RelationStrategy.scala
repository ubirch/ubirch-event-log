package com.ubirch.discovery.process

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.discovery.models.{ Relation, RelationElem }
import com.ubirch.discovery.util.DiscoveryJsonSupport
import com.ubirch.discovery.util.Exceptions.{ MasterTreeStrategyException, SlaveTreeStrategyException, UPPStrategyException }
import com.ubirch.models.{ EventLog, Values }
import com.ubirch.protocol.ProtocolMessage

import scala.util.{ Failure, Success, Try }

sealed trait RelationStrategy {
  def eventLog: EventLog
  def create: Seq[Relation]
}

object RelationStrategy {
  def getStrategy(eventLog: EventLog): RelationStrategy = {
    eventLog match {
      case el if el.category == Values.UPP_CATEGORY => UPPStrategy(el)
      case el if el.category == Values.SLAVE_TREE_CATEGORY => SlaveTreeStrategy(el)
      case el if el.category == Values.MASTER_TREE_CATEGORY => MasterTreeStrategy(el)
      case el if el.category == Values.PUBLIC_CHAIN_CATEGORY => PublicBlockchainStrategy(el)
      case el => UnknownStrategy(el)
    }
  }
}

case class UPPStrategy(eventLog: EventLog) extends RelationStrategy with LazyLogging {

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

    val relation1 = Relation(
      vFrom = RelationElem(Option(Values.UPP_CATEGORY), Map(
        Values.HASH -> eventLog.id,
        Values.SIGNATURE -> signature,
        Values.TYPE -> Values.UPP_CATEGORY
      )),
      vTo = RelationElem(Option(Values.DEVICE_CATEGORY), Map(
        Values.DEVICE_ID -> device,
        Values.TYPE -> Values.DEVICE_CATEGORY
      )),
      edge = RelationElem(Option(Values.DEVICE_CATEGORY), Map.empty)
    )

    val maybeRelation2 = maybeChain.map { chain =>
      Relation(
        vFrom = relation1.vFrom,
        vTo = RelationElem(Option(Values.UPP_CATEGORY), Map(
          Values.SIGNATURE -> chain,
          Values.TYPE -> Values.UPP_CATEGORY
        )),
        edge = RelationElem(Option(Values.CHAIN_CATEGORY), Map.empty)
      )
    }

    Seq(relation1) ++ maybeRelation2.toSeq
  }

  override def create: Seq[Relation] = {
    try {
      get
    } catch {
      case e: Exception =>
        logger.error("Relation Creation Error: ", e)
        throw UPPStrategyException("Relation Creation Error", e.getMessage)

    }

  }
}

case class SlaveTreeStrategy(eventLog: EventLog) extends RelationStrategy with LazyLogging {

  def relation(hash: String, signature: String) = Relation(
    vFrom = RelationElem(
      Option(Values.SLAVE_TREE_CATEGORY), Map(
        Values.HASH -> eventLog.id,
        Values.TYPE -> Values.SLAVE_TREE_CATEGORY
      )
    ),
    vTo = RelationElem(
      Option(Values.UPP_CATEGORY), Map(
        Values.HASH -> hash,
        Values.SIGNATURE -> signature,
        Values.TYPE -> Values.UPP_CATEGORY
      )
    ),
    edge = RelationElem(Option(Values.SLAVE_TREE_CATEGORY), Map.empty)
  )

  def get = eventLog.lookupKeys
    .find(_.category == Values.SLAVE_TREE_CATEGORY)
    .map(_.value)
    .getOrElse(Nil)
    .map(x => relation(x.name, x.extra(Values.SIGNATURE)))

  override def create: Seq[Relation] = {
    try {
      get
    } catch {
      case e: Exception =>
        logger.error("Relation Creation Error: ", e)
        throw SlaveTreeStrategyException("Relation Creation Error", e.getMessage)
    }

  }
}

case class MasterTreeStrategy(eventLog: EventLog) extends RelationStrategy with LazyLogging {

  def relation(hash: String) =
    Relation(
      vFrom = RelationElem(Option(Values.MASTER_TREE_CATEGORY), Map(
        Values.HASH -> eventLog.id,
        Values.TYPE -> Values.MASTER_TREE_CATEGORY
      )),
      vTo = RelationElem(Option(Values.SLAVE_TREE_CATEGORY), Map(
        Values.HASH -> hash,
        Values.TYPE -> Values.SLAVE_TREE_CATEGORY
      )),
      edge = RelationElem(Option(Values.MASTER_TREE_CATEGORY), Map.empty)
    )

  def get = eventLog.lookupKeys
    .find(_.category == Values.MASTER_TREE_CATEGORY)
    .map(_.value)
    .getOrElse(Nil)
    .map(x => relation(x.name))

  override def create: Seq[Relation] = {
    try {
      get
    } catch {
      case e: Exception =>
        logger.error("Relation Creation Error: ", e)
        throw MasterTreeStrategyException("Relation Creation Error", e.getMessage)
    }
  }
}

case class PublicBlockchainStrategy(eventLog: EventLog) extends RelationStrategy with LazyLogging {
  def relation(hash: String) =
    Relation(
      vFrom = RelationElem(Option(Values.PUBLIC_CHAIN_CATEGORY), Map(
        Values.HASH -> eventLog.id,
        Values.TYPE -> Values.PUBLIC_CHAIN_CATEGORY,
        Values.PUBLIC_CHAIN_CATEGORY -> eventLog.category
      )),
      vTo = RelationElem(Option(Values.MASTER_TREE_CATEGORY), Map(
        Values.HASH -> hash,
        Values.TYPE -> Values.MASTER_TREE_CATEGORY
      )),
      edge = RelationElem(Option(Values.PUBLIC_CHAIN_CATEGORY), Map.empty)
    )

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
        throw MasterTreeStrategyException("Relation Creation Error", e.getMessage)
    }
  }
}

case class UnknownStrategy(eventLog: EventLog) extends RelationStrategy {
  override def create: Seq[Relation] = throw new Exception("There's no strategy for category " + eventLog.category)
}
