package com.ubirch.lookup.models

sealed trait QueryType {
  val value: String
}

case object Payload extends QueryType {
  override val value: String = "payload"
}

case object Signature extends QueryType {
  override val value: String = "signature"
}

object QueryType {

  def isValid(value: String): Boolean = fromString(value).isDefined

  def fromString(value: String): Option[QueryType] = {
    value.toLowerCase match {
      case Payload.value => Option(Payload)
      case Signature.value => Option(Signature)
      case _ => None
    }
  }

  val QUERY_TYPE_HEADER = "query-type"

}