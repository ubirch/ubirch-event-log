package com.ubirch.chainer.models

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.chainer.util.ChainerJsonSupport
import com.ubirch.models.{ EventLog, Value, Values }
import com.ubirch.protocol.ProtocolMessage

import scala.util.{ Failure, Success, Try }

/**
 * Represents a Strategy for creating values objects
 * @tparam T Represents the incoming type
 */
trait ValueStrategy[T] {
  def create(value: T): Seq[Value]
}

/**
 * Represents the kind of strategy for when the incoming object is an event log.
 */
trait EventLogValueStrategy extends ValueStrategy[EventLog] {
  def create(eventLog: EventLog): Seq[Value]
}

/**
 * Companion object for the Value Strategy
 */
object ValueStrategy {
  def getStrategyForNormalLeaves(mode: Mode): EventLogValueStrategy = {
    mode match {
      case Slave => SlaveTreeStrategy()
      case Master => OtherStrategy()
    }
  }
}

/**
 * Represents the strategy for a foundation tree
 */
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

/**
 * Represents a default strategy, sort of like a pass-through.
 */
case class OtherStrategy() extends EventLogValueStrategy {
  override def create(eventLog: EventLog): Seq[Value] = Seq(Value(eventLog.id, Option(eventLog.category), Map.empty))
}
