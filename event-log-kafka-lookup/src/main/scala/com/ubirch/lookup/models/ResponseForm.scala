package com.ubirch.lookup.models

import com.ubirch.models.Values

sealed trait ResponseForm {
  val value: String
}

case object AnchorsWithPath extends ResponseForm {
  val value: String = Values.RESPONSE_ANCHORS_WITH_PATH
}
case object AnchorsNoPath extends ResponseForm {
  val value: String = Values.RESPONSE_ANCHORS_NO_PATH
}

object ResponseForm {

  def isValid(value: String): Boolean = fromString(value).isDefined

  def fromString(value: String): Option[ResponseForm] = {
    value.toLowerCase match {
      case AnchorsWithPath.value => Option(AnchorsWithPath)
      case AnchorsNoPath.value => Option(AnchorsNoPath)
      case _ => None
    }
  }

  val QUERY_RESPONSE_FORM_HEADER = "query-response-form-header"

}
