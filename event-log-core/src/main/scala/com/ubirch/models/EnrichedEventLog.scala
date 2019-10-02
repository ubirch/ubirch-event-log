package com.ubirch.models

import java.text.SimpleDateFormat

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

  def withBigBangTime: EventLog = {
    val sdf = new SimpleDateFormat("dd-M-yyyy hh:mm:ss")
    val newDate = sdf.parse("02-03-1986 00:00:00")
    eventLog.withEventTime(newDate)
  }

  def addBigBangLookup: EventLog = {
    import LookupKey._
    val lk = LookupKey(
      Values.BIG_BANG_MASTER_TREE_ID,
      Values.BIG_BANG_MASTER_TREE_CATEGORY,
      eventLog.id.asKeyWithLabel(Values.BIG_BANG_MASTER_TREE_CATEGORY),
      Seq(Values.BIG_BANG_MASTER_TREE_CATEGORY.asValueWithLabel(Values.BIG_BANG_MASTER_TREE_CATEGORY))
    )
    eventLog.addLookupKeys(lk)
  }

}

object EnrichedEventLog {

  implicit def enrichedEventLog(eventLog: EventLog): EnrichedEventLog = EnrichedEventLog(eventLog)
}
