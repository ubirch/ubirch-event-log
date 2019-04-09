package com.ubirch.models

import com.typesafe.config.Config
import com.ubirch.util.SigningHelper

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

}

object EnrichedEventLog {

  implicit def enrichedEventLog(eventLog: EventLog): EnrichedEventLog = EnrichedEventLog(eventLog)
}
