package com.ubirch.lookup.models

import org.json4s.JsonAST.{ JNull, JValue }

case class LookupResult(key: String, queryType: QueryType, event: Option[JValue])

object LookupResult {

  def NotFound(key: String, queryType: QueryType) = LookupResult(key, queryType, Option(JNull))

  def Found(key: String, queryType: QueryType, event: JValue) = LookupResult(key, queryType, Option(event))

}
