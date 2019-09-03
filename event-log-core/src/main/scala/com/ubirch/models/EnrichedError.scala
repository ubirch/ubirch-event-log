package com.ubirch.models

import com.ubirch.util.EventLogJsonSupport

import scala.language.implicitConversions

case class EnrichedError(error: Error) {

  def toEventLog(category: String): EventLog = {
    val payload = EventLogJsonSupport.ToJson[Error](error).get
    EventLog(getClass.getName, category, payload).withIdAsCustomerId
  }

}

object EnrichedError {
  implicit def enrichedError(error: Error): EnrichedError = EnrichedError(error)

}

