package com.ubirch.lookup.models

import org.json4s.JValue
import org.json4s.JsonAST.JNull

case class LookupResult(key: String, queryType: String, message: String, event: Option[JValue], anchors: Seq[JValue])

object LookupResult {

  def apply(key: String, queryType: QueryType, message: String, event: Option[JValue], anchors: Seq[JValue]): LookupResult = LookupResult(key, queryType.value, message, event, anchors)

  def NotFound(key: String, queryType: QueryType) = LookupResult(key, queryType.value, "Nothing Found", Option(JNull), Nil)

  def Found(key: String, queryType: QueryType, event: JValue, anchors: Seq[JValue]) = LookupResult(key, queryType.value, "Query Successfully Processed", Option(event), anchors)

  def NoEvent(key: String, queryType: QueryType, message: String) = LookupResult(key, queryType.value, message, Option(JNull), Nil)

}
