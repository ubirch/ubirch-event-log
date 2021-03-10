package com.ubirch.encoder

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.encoder.process.EncoderHelper
import com.ubirch.encoder.services.kafka.consumer.EncoderPipeData
import com.ubirch.kafka.MessageEnvelope
import com.ubirch.models.{ LookupKey, Values }
import com.ubirch.protocol.ProtocolMessage
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.record.TimestampType
import org.json4s.JsonAST.{ JObject, JString, JValue }
import com.ubirch.models.LookupKey.Helpers

import java.util.UUID

class EncodeHelperSpec extends TestBase with LazyLogging {
  "Create payload hash" must {
    "succeed to create payload hash" in {
      val payload = 1
      val pm = new ProtocolMessage(1, UUID.randomUUID(), 0, payload)
      pm.setSignature(org.bouncycastle.util.Strings.toByteArray("1111"))
      val customerId = UUID.randomUUID().toString
      val ctxt = JObject("customerId" -> JString(customerId))
      val envelop = MessageEnvelope(pm, ctxt)

      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray)
      val encoderPipeData = EncoderPipeData(Vector(customerRecord), Vector.empty[JValue])
      val payloadHash = EncoderHelper.createPayloadHash(envelop, encoderPipeData)
      assert(payloadHash.isSuccess)
      assert(payloadHash.get == payload.toString)
    }

    "fail to create payload hash - messageEnvelop is null" in {
      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray)
      val encoderPipeData = EncoderPipeData(Vector(customerRecord), Vector.empty[JValue])
      val payloadHash = EncoderHelper.createPayloadHash(null, encoderPipeData)
      assert(payloadHash.isFailure)
    }

    "fail to create payload hash - ubirch pack is null" in {
      val customerId = UUID.randomUUID().toString
      val ctxt = JObject("customerId" -> JString(customerId))
      val envelop = MessageEnvelope(null, ctxt)

      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray)
      val encoderPipeData = EncoderPipeData(Vector(customerRecord), Vector.empty[JValue])
      val payloadHash = EncoderHelper.createPayloadHash(envelop, encoderPipeData)
      assert(payloadHash.isFailure)
    }

    "fail to create payload hash - payload is null" in {
      val pm = new ProtocolMessage(1, UUID.randomUUID(), 0, null)
      pm.setSignature(org.bouncycastle.util.Strings.toByteArray("1111"))
      val customerId = UUID.randomUUID().toString
      val ctxt = JObject("customerId" -> JString(customerId))
      val envelop = MessageEnvelope(pm, ctxt)

      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray)
      val encoderPipeData = EncoderPipeData(Vector(customerRecord), Vector.empty[JValue])
      val payloadHash = EncoderHelper.createPayloadHash(envelop, encoderPipeData)
      assert(payloadHash.isFailure)
    }
  }

  "Create Signature Lookup Key" must {
    "succeed to create signature lookup key" in {
      val payload = 1
      val pm = new ProtocolMessage(1, UUID.randomUUID(), 0, payload)
      val signature = org.bouncycastle.util.Strings.toByteArray("1111")
      pm.setSignature(signature)
      val customerId = UUID.randomUUID().toString
      val ctxt = JObject("customerId" -> JString(customerId))
      val envelop = MessageEnvelope(pm, ctxt)

      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray)
      val encoderPipeData = EncoderPipeData(Vector(customerRecord), Vector.empty[JValue])

      val signatureLookupKey = EncoderHelper.createSignatureLookupKey(payload.toString, envelop, encoderPipeData, Values.UPP_CATEGORY)

      val lookUpKeys = Seq(LookupKey(Values.SIGNATURE, Values.UPP_CATEGORY, payload.toString.asKey, Seq(org.bouncycastle.util.encoders.Base64.toBase64String(signature).asValue)).categoryAsKeyLabel.nameAsValueLabelForAll)
      assert(signatureLookupKey.isSuccess)
      assert(signatureLookupKey.get == lookUpKeys)
    }

    "fail to create signature lookup key - message envelope is null" in {
      val payload = 1

      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray)
      val encoderPipeData = EncoderPipeData(Vector(customerRecord), Vector.empty[JValue])

      val signatureLookupKey = EncoderHelper.createSignatureLookupKey(payload.toString, null, encoderPipeData, Values.UPP_CATEGORY)

      assert(signatureLookupKey.isFailure)
    }

    "signature becomes empty - UPP is null" in {
      val payload = 1
      val customerId = UUID.randomUUID().toString
      val ctxt = JObject("customerId" -> JString(customerId))
      val envelop = MessageEnvelope(null, ctxt)

      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray)
      val encoderPipeData = EncoderPipeData(Vector(customerRecord), Vector.empty[JValue])

      val signatureLookupKey = EncoderHelper.createSignatureLookupKey(payload.toString, envelop, encoderPipeData, Values.UPP_CATEGORY)

      assert(signatureLookupKey.isSuccess)
      assert(signatureLookupKey.get == Seq.empty[String])
    }

    "signature becomes empty - signature is null" in {
      val payload = 1
      val pm = new ProtocolMessage(1, UUID.randomUUID(), 0, payload)
      pm.setSignature(null)
      val customerId = UUID.randomUUID().toString
      val ctxt = JObject("customerId" -> JString(customerId))
      val envelop = MessageEnvelope(pm, ctxt)

      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray)
      val encoderPipeData = EncoderPipeData(Vector(customerRecord), Vector.empty[JValue])

      val signatureLookupKey = EncoderHelper.createSignatureLookupKey(payload.toString, envelop, encoderPipeData, Values.UPP_CATEGORY)

      assert(signatureLookupKey.isSuccess)
      assert(signatureLookupKey.get == Seq.empty[String])
    }
  }

  "Create Device Lookup Key" must {
    "succeed to create device lookup key" in {
      val payload = 1
      val pm = new ProtocolMessage(1, UUID.randomUUID(), 0, payload)
      val deviceId = UUID.randomUUID()
      pm.setUUID(deviceId)
      val customerId = UUID.randomUUID().toString
      val ctxt = JObject("customerId" -> JString(customerId))
      val envelop = MessageEnvelope(pm, ctxt)

      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray)
      val encoderPipeData = EncoderPipeData(Vector(customerRecord), Vector.empty[JValue])

      val deviceLookupKey = EncoderHelper.createDeviceLookupKey(payload.toString, envelop, encoderPipeData, Values.UPP_CATEGORY)

      val lookUpKeys = Seq(LookupKey(Values.DEVICE_ID, Values.DEVICE_CATEGORY, deviceId.toString.asKey, Seq(payload.toString.asValue)).withKeyLabel(Values.UPP_CATEGORY).categoryAsKeyLabel.addValueLabelForAll(Values.UPP_CATEGORY))
      assert(deviceLookupKey.isSuccess)
      assert(deviceLookupKey.get == lookUpKeys)
    }

    "fail to create device lookup key - message envelope is null" in {
      val payload = 1

      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray)
      val encoderPipeData = EncoderPipeData(Vector(customerRecord), Vector.empty[JValue])

      val deviceLookupKey = EncoderHelper.createDeviceLookupKey(payload.toString, null, encoderPipeData, Values.UPP_CATEGORY)

      assert(deviceLookupKey.isFailure)
    }

    "device becomes empty - UPP is null" in {
      val payload = 1
      val customerId = UUID.randomUUID().toString
      val ctxt = JObject("customerId" -> JString(customerId))
      val envelop = MessageEnvelope(null, ctxt)

      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray)
      val encoderPipeData = EncoderPipeData(Vector(customerRecord), Vector.empty[JValue])

      val deviceLookupKey = EncoderHelper.createDeviceLookupKey(payload.toString, envelop, encoderPipeData, Values.UPP_CATEGORY)

      assert(deviceLookupKey.isSuccess)
      assert(deviceLookupKey.get == Seq.empty[String])
    }

    "fail to create device lookup key - device is null" in {
      val payload = 1
      val pm = new ProtocolMessage(1, UUID.randomUUID(), 0, payload)
      pm.setUUID(null)
      val customerId = UUID.randomUUID().toString
      val ctxt = JObject("customerId" -> JString(customerId))
      val envelop = MessageEnvelope(pm, ctxt)

      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray)
      val encoderPipeData = EncoderPipeData(Vector(customerRecord), Vector.empty[JValue])

      val deviceLookupKey = EncoderHelper.createDeviceLookupKey(payload.toString, envelop, encoderPipeData, Values.UPP_CATEGORY)

      assert(deviceLookupKey.isFailure)
    }
  }

  "Create Chain Lookup Key" must {
    "succeed to create chain lookup key" in {
      val payload = 1
      val pm = new ProtocolMessage(1, UUID.randomUUID(), 0, payload)
      val chain = org.bouncycastle.util.Strings.toByteArray("this is my chain")
      pm.setChain(chain)
      val customerId = UUID.randomUUID().toString
      val ctxt = JObject("customerId" -> JString(customerId))
      val envelop = MessageEnvelope(pm, ctxt)

      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray)
      val encoderPipeData = EncoderPipeData(Vector(customerRecord), Vector.empty[JValue])

      val chainLookupKey = EncoderHelper.createChainLookupKey(payload.toString, envelop, encoderPipeData, Values.UPP_CATEGORY)

      val lookUpKeys = Seq(LookupKey(Values.UPP_CHAIN, Values.CHAIN_CATEGORY, payload.toString.asKey, Seq(org.bouncycastle.util.encoders.Base64.toBase64String(chain).asValue)).withKeyLabel(Values.UPP_CATEGORY).categoryAsValueLabelForAll)
      assert(chainLookupKey.isSuccess)
      assert(chainLookupKey.get == lookUpKeys)
    }

    "fail to create chain lookup key - message envelope is null" in {
      val payload = 1

      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray)
      val encoderPipeData = EncoderPipeData(Vector(customerRecord), Vector.empty[JValue])

      val chainLookupKey = EncoderHelper.createChainLookupKey(payload.toString, null, encoderPipeData, Values.UPP_CATEGORY)

      assert(chainLookupKey.isFailure)
    }

    "chain becomes empty - UPP is null" in {
      val payload = 1
      val customerId = UUID.randomUUID().toString
      val ctxt = JObject("customerId" -> JString(customerId))
      val envelop = MessageEnvelope(null, ctxt)

      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray)
      val encoderPipeData = EncoderPipeData(Vector(customerRecord), Vector.empty[JValue])

      val chainLookupKey = EncoderHelper.createChainLookupKey(payload.toString, envelop, encoderPipeData, Values.UPP_CATEGORY)

      assert(chainLookupKey.isSuccess)
      assert(chainLookupKey.get == Seq.empty[String])
    }

    "chain becomes empty - chain is null" in {
      val payload = 1
      val pm = new ProtocolMessage(1, UUID.randomUUID(), 0, payload)
      pm.setChain(null)
      val customerId = UUID.randomUUID().toString
      val ctxt = JObject("customerId" -> JString(customerId))
      val envelop = MessageEnvelope(pm, ctxt)

      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray)
      val encoderPipeData = EncoderPipeData(Vector(customerRecord), Vector.empty[JValue])

      val chainLookupKey = EncoderHelper.createChainLookupKey(payload.toString, envelop, encoderPipeData, Values.UPP_CATEGORY)

      assert(chainLookupKey.isSuccess)
      assert(chainLookupKey.get == Seq.empty[String])
    }
  }
}
