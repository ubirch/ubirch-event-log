package com.ubirch.verification.services.eventlog

import com.ubirch.models.Values
import com.ubirch.verification.models._
import com.ubirch.verification.util.LookupJsonSupport
import org.json4s.JValue

import scala.concurrent.Future

trait EventLogClient {

  def getEventByHash(
      hash: Array[Byte],
      queryDepth: QueryDepth,
      responseForm: ResponseForm,
      blockchainInfo: BlockchainInfo
  ): Future[LookupResult]

  def getEventBySignature(
      signature: Array[Byte],
      queryDepth: QueryDepth,
      responseForm: ResponseForm,
      blockchainInfo: BlockchainInfo
  ): Future[LookupResult]

}

object EventLogClient {
  def shortestPathAsJValue(maybeAnchors: Seq[VertexStruct]): JValue =
    LookupJsonSupport.ToJson[Seq[DumbVertexStruct]](maybeAnchors.map(_.toDumbVertexStruct)).get

  def shortestPathAsJValue(path: Seq[VertexStruct], maybeAnchors: Seq[VertexStruct]): JValue = {
    val anchors = Map(
      Values.SHORTEST_PATH -> path.map(v => v.toDumbVertexStruct),
      Values.BLOCKCHAINS -> maybeAnchors.map(v => v.toDumbVertexStruct)
    )
    LookupJsonSupport.ToJson(anchors).get
  }

  def upperAndLowerAsJValue(upperPath: Seq[VertexStruct], upperBlocks: Seq[VertexStruct], lowerPath: Seq[VertexStruct], lowerBlocks: Seq[VertexStruct]): JValue = {
    val anchors = Map(
      Values.UPPER_PATH -> upperPath.map(_.toDumbVertexStruct),
      Values.UPPER_BLOCKCHAINS -> upperBlocks.map(_.toDumbVertexStruct),
      Values.LOWER_PATH -> lowerPath.map(_.toDumbVertexStruct),
      Values.LOWER_BLOCKCHAINS -> lowerBlocks.map(_.toDumbVertexStruct)
    )
    LookupJsonSupport.ToJson(anchors).get
  }

  def upperAndLowerAsJValue(upperBlocks: Seq[VertexStruct], lowerBlocks: Seq[VertexStruct]): JValue = {
    val anchors = Map(
      Values.UPPER_BLOCKCHAINS -> upperBlocks.map(_.toDumbVertexStruct),
      Values.LOWER_BLOCKCHAINS -> lowerBlocks.map(_.toDumbVertexStruct)
    )
    LookupJsonSupport.ToJson(anchors).get
  }
}
