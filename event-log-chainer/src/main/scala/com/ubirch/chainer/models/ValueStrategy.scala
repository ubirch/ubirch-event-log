package com.ubirch.chainer.models

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.chainer.util.ChainerJsonSupport
import com.ubirch.models.{ EventLog, Value, Values }
import com.ubirch.protocol.ProtocolMessage

import scala.util.{ Failure, Success, Try }

trait ValueStrategy {
  def create(eventLog: EventLog): Seq[Value]
}

object ValueStrategy {
  def getStrategy(mode: Mode): ValueStrategy = {
    mode match {
      case Slave => SlaveTreeStrategy()
      case Master => OtherStrategy()
    }
  }
}

case class SlaveTreeStrategy() extends ValueStrategy with LazyLogging {
  override def create(eventLog: EventLog): Seq[Value] = {

    val ubirchPacket = ChainerJsonSupport.FromJson[ProtocolMessage](eventLog.event).get

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

case class OtherStrategy() extends ValueStrategy {
  override def create(eventLog: EventLog): Seq[Value] = Seq(Value(eventLog.id, Option(eventLog.category), Map.empty))
}
