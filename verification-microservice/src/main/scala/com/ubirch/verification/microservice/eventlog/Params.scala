package com.ubirch.verification.microservice.eventlog

trait Params {
  val value: String
}

abstract class ParamsHelper[T <: Params](val HEADER: String) {
  def isValid(value: String): Boolean = fromString(value).isDefined

  def fromString(value: String): Option[T] = options.find(_.value == value)

  def options: List[T]
}

sealed trait QueryDepth extends Params

case object Simple extends QueryDepth {
  final val value: String = "query-depth-simple"
}

case object ShortestPath extends QueryDepth {
  final val value: String = "query-depth-shortest-path"
}

case object UpperLower extends QueryDepth {
  final val value: String = "query-depth-upper-lower"
}

object QueryDepth extends ParamsHelper[QueryDepth]("query-depth") {
  override def options: List[QueryDepth] = List(Simple, ShortestPath, UpperLower)
}

sealed trait ResponseForm extends Params

case object AnchorsWithPath extends ResponseForm {
  final val value: String = "anchors_with_path"
}

case object AnchorsNoPath extends ResponseForm {
  final val value: String = "anchors_no_path"
}

object ResponseForm extends ParamsHelper[ResponseForm]("query-response-form-header") {
  override def options: List[ResponseForm] = List(AnchorsWithPath, AnchorsNoPath)
}

sealed trait BlockchainInfo extends Params

case object Extended extends BlockchainInfo {
  final val value: String = "ext"
}

case object Normal extends BlockchainInfo {
  final val value: String = "normal"
}

object BlockchainInfo extends ParamsHelper[BlockchainInfo]("query-blockchain-info") {
  override def options: List[BlockchainInfo] = List(Normal, Extended)
}