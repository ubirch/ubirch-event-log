package com.ubirch.models

import com.typesafe.config.Config
import com.ubirch.util.{ SigningHelper, TimeHelper, UUIDHelper }

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

  def addRequestIdHeader(requestId: String): EventLog = {
    eventLog.addHeaders(HeaderNames.REQUEST_ID -> requestId)
  }

  def addRequestIdHeaderIf(predicate: => Boolean, requestId: String): EventLog = {
    eventLog.addHeadersIf(predicate, HeaderNames.REQUEST_ID -> requestId)
  }

  def addBlueMark: EventLog = {
    eventLog.addHeaders(HeaderNames.BLUE_MARK -> UUIDHelper.randomUUID.toString)
  }

  def withBigBangTime: EventLog = {
    eventLog.withEventTime(TimeHelper.bigBangAsDate.toDate)
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
