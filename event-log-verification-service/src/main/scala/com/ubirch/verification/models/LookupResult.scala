package com.ubirch.verification.models

import org.json4s.JValue
import org.json4s.JsonAST.JNull

case class LookupResult(success: Boolean, key: String, queryType: String, message: String, event: Option[JValue], anchors: Option[JValue])

object LookupResult {

  def apply(success: Boolean, key: String, queryType: QueryType, message: String, event: Option[JValue], anchors: Option[JValue]): LookupResult =
    LookupResult(success, key, queryType.value, message, event, anchors)

  def NotFound(key: String, queryType: QueryType): LookupResult =
    LookupResult(success = true, key, queryType.value, "Nothing Found", Option(JNull), Option(JNull))

  def Found(key: String, queryType: QueryType, event: JValue, anchors: JValue): LookupResult =
    LookupResult(success = true, key, queryType.value, "Query Successfully Processed", Option(event), Option(anchors))

  def Error(key: String, queryType: QueryType, message: String): LookupResult =
    LookupResult(success = false, key, queryType.value, message, Option(JNull), Option(JNull))

}
