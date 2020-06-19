package com.ubirch.verification.service.models

import com.ubirch.models.Values
import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.collection.JavaConverters._

trait Params {
  val value: String
}

object Params {
  def get[T <: Params](maybeConsumerRecord: Option[ConsumerRecord[String, String]], paramsHelper: ParamsHelper[T]): Option[T] = {
    maybeConsumerRecord
      .flatMap(_.headers().headers(paramsHelper.HEADER).asScala.headOption)
      .map(_.value())
      .map(org.bouncycastle.util.Strings.fromUTF8ByteArray)
      .filter(paramsHelper.isValid)
      .flatMap(paramsHelper.fromString)
  }

  def getOrElse[T <: Params](maybeConsumerRecord: Option[ConsumerRecord[String, String]], paramsHelper: ParamsHelper[T], orElse: T): T =
    get(maybeConsumerRecord, paramsHelper).getOrElse(orElse)

}

abstract class ParamsHelper[T <: Params](val HEADER: String) {
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

object QueryType extends ParamsHelper[QueryType]("query-type") {
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

object QueryDepth extends ParamsHelper[QueryDepth]("query-depth") {
  def options: List[QueryDepth] = List(Simple, ShortestPath, UpperLower)
}

sealed trait ResponseForm extends Params

case object AnchorsWithPath extends ResponseForm {
  val value: String = Values.RESPONSE_ANCHORS_WITH_PATH
}

case object AnchorsNoPath extends ResponseForm {
  val value: String = Values.RESPONSE_ANCHORS_NO_PATH
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

