package com.ubirch.verification.models

import com.ubirch.models.Values

trait Params {
  val value: String
}

trait ParamsHelper[T <: Params] {
  def isValid(value: String): Boolean = fromString(value).isDefined

  def fromString(value: String): Option[T] = options.find(_.value == value)

  def options: List[T]
}

sealed trait QueryType extends Params

case object Payload extends QueryType {
  val value: String = Values.PAYLOAD
}

case object Signature extends QueryType {
  val value: String = Values.SIGNATURE
}

object QueryType extends ParamsHelper[QueryType] {
  def options: List[QueryType] = List(Payload, Signature)
}

sealed trait QueryDepth extends Params

case object Simple extends QueryDepth {
  val value: String = Values.QUERY_DEPTH_SIMPLE
}

case object ShortestPath extends QueryDepth {
  val value: String = Values.QUERY_DEPTH_SHORTEST_PATH
}

case object UpperLower extends QueryDepth {
  val value: String = Values.QUERY_DEPTH_SHORTEST_UPPER_LOWER
}

object QueryDepth extends ParamsHelper[QueryDepth] {
  def options: List[QueryDepth] = List(Simple, ShortestPath, UpperLower)
}

sealed trait ResponseForm extends Params

case object AnchorsWithPath extends ResponseForm {
  val value: String = Values.RESPONSE_ANCHORS_WITH_PATH
}

case object AnchorsNoPath extends ResponseForm {
  val value: String = Values.RESPONSE_ANCHORS_NO_PATH
}

object ResponseForm extends ParamsHelper[ResponseForm] {
  override def options: List[ResponseForm] = List(AnchorsWithPath, AnchorsNoPath)
}

sealed trait BlockchainInfo extends Params

case object Extended extends BlockchainInfo {
  final val value: String = "ext"
}

case object Normal extends BlockchainInfo {
  final val value: String = "normal"
}

object BlockchainInfo extends ParamsHelper[BlockchainInfo] {
  override def options: List[BlockchainInfo] = List(Normal, Extended)
}

