package com.ubirch.lookup.models

import org.json4s.JValue
import org.json4s.JsonAST.JNull

case class LookupResult(success: Boolean, key: String, queryType: String, message: String, event: Option[JValue], anchors: Seq[JValue])

object LookupResult {

  def apply(success: Boolean, key: String, queryType: QueryType, message: String, event: Option[JValue], anchors: Seq[JValue]): LookupResult = {
    LookupResult(success, key, queryType.value, message, event, anchors)
  }

  def NotFound(key: String, queryType: QueryType) = LookupResult(success = true, key, queryType.value, "Nothing Found", Option(JNull), Nil)

  def Found(key: String, queryType: QueryType, event: JValue, anchors: Seq[JValue]) = LookupResult(success = true, key, queryType.value, "Query Successfully Processed", Option(event), anchors)

  def Error(key: String, queryType: QueryType, message: String) = LookupResult(success = false, key, queryType.value, message, Option(JNull), Nil)

}
