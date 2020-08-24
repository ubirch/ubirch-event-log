package com.ubirch.verification.models

import org.json4s.JValue
import org.json4s.JsonAST.JNull

case class LookupResult(success: Boolean, value: String, queryType: String, message: String, event: JValue, anchors: JValue)

object LookupResult {

  def apply(success: Boolean, value: String, queryType: QueryType, message: String, event: JValue, anchors: JValue): LookupResult =
    LookupResult(success, value, queryType.value, message, event, anchors)

  def NotFound(value: String, queryType: QueryType): LookupResult =
    LookupResult(success = true, value, queryType.value, "Nothing Found", JNull, JNull)

  def Found(value: String, queryType: QueryType, event: JValue, anchors: JValue): LookupResult =
    LookupResult(success = true, value, queryType.value, "Query Successfully Processed", event, anchors)

  def Error(value: String, queryType: QueryType, message: String): LookupResult =
    LookupResult(success = false, value, queryType.value, message, JNull, JNull)

}
