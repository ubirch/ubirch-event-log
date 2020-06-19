package com.ubirch.verification.service.eventlog

import com.avsystem.commons.serialization.GenCodec
import com.avsystem.commons.serialization.json.{JsonBinaryFormat, JsonOptions, JsonStringInput, RawJson}
import com.fasterxml.jackson.databind.{DeserializationFeature, ObjectMapper}
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.verification.service.models.{BlockchainInfo, QueryDepth, ResponseForm}
import io.udash.rest.raw.JsonValue

import scala.concurrent.Future

trait EventLogClient {

  def getEventByHash(
                      hash: Array[Byte],
                      queryDepth: QueryDepth,
                      responseForm: ResponseForm,
                      blockchainInfo: BlockchainInfo
                    ): Future[EventLogClient.Response]

  def getEventBySignature(
                           signature: Array[Byte],
                           queryDepth: QueryDepth,
                           responseForm: ResponseForm,
                           blockchainInfo: BlockchainInfo
                         ): Future[EventLogClient.Response]
}

object EventLogClient {

  case class Response(success: Boolean, message: String, data: Data)

  object Response {
    implicit val codec: GenCodec[Response] = GenCodec.materialize

    private val jsonConfig = JsonOptions(binaryFormat = JsonBinaryFormat.Base64())

    def fromJson(s: String): Response = JsonStringInput.read[Response](s, jsonConfig)
  }

  case class Data(key: String, query_type: String, event: ProtocolMessage, anchors: JsonValue)

  object Data {
    implicit val codec: GenCodec[Data] = GenCodec.materialize

    private val OM = new ObjectMapper()
    OM.configure(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS, true)

    implicit val protocolMessageCodec: GenCodec[ProtocolMessage] = GenCodec.create(
      i => OM.readValue(i.readCustom(RawJson).getOrElse(i.readSimple().readString()), classOf[ProtocolMessage]),
      (o, v) => if (!o.writeCustom(RawJson, OM.writeValueAsString(v))) o.writeSimple().writeString(OM.writeValueAsString(v))
    )
  }

}
