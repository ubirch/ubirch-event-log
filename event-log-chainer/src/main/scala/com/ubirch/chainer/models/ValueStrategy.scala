package com.ubirch.chainer.models

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.chainer.util.ChainerJsonSupport
import com.ubirch.models.{ EventLog, Value, Values }
import com.ubirch.protocol.ProtocolMessage

import scala.util.{ Failure, Success, Try }

trait ValueStrategy[T] {
  def create(value: T): Seq[Value]
}

trait EventLogValueStrategy extends ValueStrategy[EventLog] {
  def create(eventLog: EventLog): Seq[Value]
}

trait StringValueStrategy extends ValueStrategy[String] {
  def create(zero: String): Seq[Value]
}

object ValueStrategy {
  def getStrategyForNormalLeaves(mode: Mode): EventLogValueStrategy = {
    mode match {
      case Slave => SlaveTreeStrategy()
      case Master => OtherStrategy()
    }
  }

  def getStrategyForZero(mode: Mode): StringValueStrategy = {
    mode match {
      case Slave => ZeroOnSlave()
      case Master => ZeroOnMaster()
    }
  }
}

case class SlaveTreeStrategy() extends EventLogValueStrategy with LazyLogging {
  override def create(eventLog: EventLog): Seq[Value] = {

    val ubirchPacket = try {
      ChainerJsonSupport.FromJson[ProtocolMessage](eventLog.event).get
    } catch {
      case e: Exception =>
        logger.error(s"SlaveTreeStrategy: Seems not to be a UPP - ${eventLog.event}", e)
        throw e
    }

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

    val res = Seq(Value(eventLog.id, Option(eventLog.category), Map(Values.SIGNATURE -> signature)))

    res
  }
}

case class OtherStrategy() extends EventLogValueStrategy {
  override def create(eventLog: EventLog): Seq[Value] = Seq(Value(eventLog.id, Option(eventLog.category), Map.empty))
}

case class ZeroOnSlave() extends StringValueStrategy {
  override def create(zero: String): Seq[Value] = Seq(Value(zero, Option(Values.SLAVE_TREE_CATEGORY), Map.empty))
}

case class ZeroOnMaster() extends StringValueStrategy {
  override def create(zero: String): Seq[Value] = Seq(Value(zero, Option(Values.MASTER_TREE_CATEGORY), Map.empty))
}
