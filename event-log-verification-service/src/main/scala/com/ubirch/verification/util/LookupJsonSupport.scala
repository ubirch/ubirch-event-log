package com.ubirch.verification.util

import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.databind.node._
import com.ubirch.models.CustomSerializers
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.util.JsonHelper
import org.json4s.CustomSerializer
import org.json4s.jackson.JsonMethods._

import scala.collection.JavaConverters._

object Custom {

  // based on JSONProtocolEncoder, but without messing with signatures
  private val protocolMessageMapper = {
    val mapper = new ObjectMapper()
    mapper.configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
    mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
    mapper.configure(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS, true)
    mapper
  }

  object Json4sProtocolMessageSerializer extends CustomSerializer[ProtocolMessage](_ => ({
    case jVal =>
      protocolMessageMapper.treeToValue[ProtocolMessage](asJsonNode(jVal), classOf[ProtocolMessage])
  }, {
    case p: ProtocolMessage =>
      val tree: ObjectNode = protocolMessageMapper.valueToTree(p)

      // workaround for Json4s not handling jackson binary nodes
      // - this can be handled better in future versions of Json4s
      def replaceBinaryNodesWithTextNodes(tree: ContainerNode[_]): Unit = {
        def inner[K](withKeys: (K => Unit) => Unit, get: K => JsonNode, set: (K, JsonNode) => Unit): Unit = {
          withKeys { key =>
            get(key) match {
              case binary: BinaryNode => set(key, new TextNode(binary.asText()))
              case containerNode: ContainerNode[_] => replaceBinaryNodesWithTextNodes(containerNode)
              case _ => // do nothing
            }
          }
        }

        tree match {
          case o: ObjectNode => inner[String](o.fieldNames().asScala.foreach, o.get, o.set)
          case a: ArrayNode => inner[Int]((0 until a.size()).foreach, a.get, a.set)
        }
      }

      replaceBinaryNodesWithTextNodes(tree)
      fromJsonNode(tree)
  }))
}

/**
  * Convenience object for managing json conversions.
  * It includes the ProtocolMessage serializer.
  */
object LookupJsonSupport extends JsonHelper(CustomSerializers.all ++ Seq(Custom.Json4sProtocolMessageSerializer))
