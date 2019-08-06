package com.ubirch.encoder

import java.util.UUID

import com.typesafe.config.Config
import com.ubirch.ConfPaths.ProducerConfPaths
import com.ubirch.encoder.services.EncoderServiceBinder
import com.ubirch.kafka.MessageEnvelope
import com.ubirch.kafka.consumer.BytesConsumer
import com.ubirch.kafka.producer.{Configs, ProducerRunner, StringProducer}
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.util.{Boot, URLsHelper}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{Deserializer, Serializer, StringSerializer}
import org.json4s.JsonAST.{JObject, JString}

import scala.language.postfixOps

/**
  * Represents an Encoder boot object.
  */
object Service extends Boot(EncoderServiceBinder.modules) {

  def main(args: Array[String]): Unit = {

    val consumer = get[BytesConsumer]

    consumer.start()

  }

}

object ServiceTest extends Boot(EncoderServiceBinder.modules) with ProducerConfPaths{


  implicit val se: Serializer[MessageEnvelope] = com.ubirch.kafka.EnvelopeSerializer
  implicit val de: Deserializer[MessageEnvelope] = com.ubirch.kafka.EnvelopeDeserializer

  val config = get[Config]

  def bootstrapServers: String = URLsHelper.passThruWithCheck(config.getString(BOOTSTRAP_SERVERS))

  def lingerMs: Int = config.getInt(LINGER_MS)

  def main(args: Array[String]): Unit = {
    def configs = Configs(bootstrapServers, lingerMs = lingerMs)

    val producer = ProducerRunner[String, MessageEnvelope](configs, Some(new StringSerializer()), Some(se))

    val range = (0 to 100000)

    try {

      range.map { x =>
        val pmId = x
        val pm = new ProtocolMessage(1, UUID.randomUUID(), 0, pmId)
        pm.setSignature(org.bouncycastle.util.Strings.toByteArray("1111"))
        val customerId = UUID.randomUUID().toString
        val ctxt = JObject("customerId" -> JString(customerId))
        val entity1 = MessageEnvelope(pm, ctxt)
        producer.getProducerOrCreate.send(new ProducerRecord[String, MessageEnvelope]("json.to.sign", entity1))

      }
    } finally {
      producer.getProducerOrCreate.close()
      System.exit(0)

    }


  }


}
