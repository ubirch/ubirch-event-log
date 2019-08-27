package com.ubirch.discovery.process

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.discovery.models.{ Relation, RelationElem }
import com.ubirch.discovery.util.DiscoveryJsonSupport
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
  override def create: Seq[Relation] = {

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
        "hash" -> eventLog.id,
        "signature" -> signature,
        "type" -> Values.UPP_CATEGORY
      )),
      vTo = RelationElem(Option(Values.DEVICE_CATEGORY), Map(
        "device-id" -> device,
        "type" -> Values.DEVICE_CATEGORY
      )),
      edge = RelationElem(Option(Values.DEVICE_CATEGORY), Map())
    )

    val maybeRelation2 = maybeChain.map { chain =>
      Relation(
        vFrom = relation1.vFrom,
        vTo = RelationElem(Option(Values.UPP_CATEGORY), Map("signature" -> chain, "type" -> Values.UPP_CATEGORY)),
        edge = RelationElem(Option(Values.CHAIN_CATEGORY), Map())
      )
    }

    Seq(relation1) ++ maybeRelation2.toSeq

  }
}

case class SlaveTreeStrategy(eventLog: EventLog) extends RelationStrategy {
  override def create(): Seq[Relation] = ???
}

case class MasterTreeStrategy(eventLog: EventLog) extends RelationStrategy {
  override def create(): Seq[Relation] = ???
}

case class PublicBlockchainStrategy(eventLog: EventLog) extends RelationStrategy {
  override def create(): Seq[Relation] = ???
}

case class UnknownStrategy(eventLog: EventLog) extends RelationStrategy {
  override def create(): Seq[Relation] = throw new Exception("There's no strategy for category " + eventLog.category)
}

