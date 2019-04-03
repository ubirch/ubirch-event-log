package com.ubirch.models

import java.nio.charset.StandardCharsets

import com.typesafe.config.Config
import com.ubirch.crypto.utils.Utils
import com.ubirch.util.SigningHelper

import scala.language.implicitConversions

case class EnrichedEventLog(eventLog: EventLog) {

  def getEventBytes: Array[Byte] = {
    eventLog.event.toString.getBytes(StandardCharsets.UTF_8)
  }

  def sign(config: Config): EventLog = {
    eventLog.withSignature(
      Utils.bytesToHex(
        SigningHelper.signData(
          config,
          getEventBytes
        )
      )
    )
  }

  def sign(pkString: String): EventLog = {
    eventLog.withSignature(
      Utils.bytesToHex(
        SigningHelper.signData(
          pkString,
          getEventBytes
        )
      )
    )
  }

}

object EnrichedEventLog {

  implicit def enrichedEventLog(eventLog: EventLog): EnrichedEventLog = EnrichedEventLog(eventLog)
}
