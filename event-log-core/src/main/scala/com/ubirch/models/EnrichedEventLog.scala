package com.ubirch.models

import com.typesafe.config.Config
import com.ubirch.util.{ SigningHelper, UUIDHelper }

import scala.language.implicitConversions

case class EnrichedEventLog(eventLog: EventLog) {

  def getEventBytes: Array[Byte] = {
    SigningHelper.getBytesFromString(eventLog.event.toString)
  }

  def sign(config: Config): EventLog = {
    eventLog.withSignature(
      SigningHelper.signAndGetAsHex(
        config,
        getEventBytes
      )
    )
  }

  def sign(pkString: String): EventLog = {
    eventLog.withSignature(
      SigningHelper.signAndGetAsHex(
        pkString,
        getEventBytes
      )
    )
  }

  def addOriginHeader(origin: String): EventLog = {
    eventLog.addHeaders(HeaderNames.ORIGIN -> origin)
  }

  def addTraceHeader(trace: String): EventLog = {
    eventLog.addHeaders(HeaderNames.TRACE -> trace)
  }

  def addBlueMark: EventLog = {
    eventLog.addHeaders(HeaderNames.BLUE_MARK -> UUIDHelper.randomUUID.toString)
  }

}

object EnrichedEventLog {

  implicit def enrichedEventLog(eventLog: EventLog): EnrichedEventLog = EnrichedEventLog(eventLog)
}
