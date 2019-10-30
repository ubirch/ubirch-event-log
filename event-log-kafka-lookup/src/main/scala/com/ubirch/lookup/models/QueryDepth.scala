package com.ubirch.lookup.models

import com.ubirch.models.Values

sealed trait QueryDepth {
  val value: String
}

case object Simple extends QueryDepth {
  val value: String = Values.QUERY_DEPTH_SIMPLE
}
case object ShortestPath extends QueryDepth {
  val value: String = Values.QUERY_DEPTH_SHORTEST_PATH
}
case object UpperLower extends QueryDepth {
  val value: String = Values.QUERY_DEPTH_SHORTEST_UPPER_LOWER
}

object QueryDepth {

  def isValid(value: String): Boolean = fromString(value).isDefined

  def fromString(value: String): Option[QueryDepth] = {
    value.toLowerCase match {
      case Simple.value => Option(Simple)
      case ShortestPath.value => Option(ShortestPath)
      case UpperLower.value => Option(UpperLower)
      case _ => None
    }
  }

  val QUERY_DEPTH_HEADER = "query-depth"

}
