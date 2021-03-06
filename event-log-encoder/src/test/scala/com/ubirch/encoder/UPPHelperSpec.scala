package com.ubirch.encoder

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.encoder.process.UPPHelper
import com.ubirch.encoder.services.kafka.consumer.EncoderPipeData
import com.ubirch.kafka.MessageEnvelope
import com.ubirch.models.{ LookupKey, Values }
import com.ubirch.protocol.ProtocolMessage
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.record.TimestampType
import org.json4s.JsonAST.{ JObject, JString, JValue }
import com.ubirch.models.LookupKey.Helpers

import java.util.UUID

class UPPHelperSpec extends TestBase with LazyLogging {
  import EncoderUtil.getDigest

  "Get category" must {
    "get categories" in {
      val pmHint0 = new ProtocolMessage(1, UUID.randomUUID(), 0, "")
      val pmHint250 = new ProtocolMessage(1, UUID.randomUUID(), 250, "")
      val pmHint251 = new ProtocolMessage(1, UUID.randomUUID(), 251, "")
      val pmHint252 = new ProtocolMessage(1, UUID.randomUUID(), 252, "")
      val ctxt = JObject()
      val uppCategory = UPPHelper.getCategory(MessageEnvelope(pmHint0, ctxt))
      val disableUppCategory = UPPHelper.getCategory(MessageEnvelope(pmHint250, ctxt))
      val enableUppCategory = UPPHelper.getCategory(MessageEnvelope(pmHint251, ctxt))
      val deleteUppCategory = UPPHelper.getCategory(MessageEnvelope(pmHint252, ctxt))
      assert(uppCategory == Some(Values.UPP_CATEGORY))
      assert(disableUppCategory == Some(Values.UPP_DISABLE_CATEGORY))
      assert(enableUppCategory == Some(Values.UPP_ENABLE_CATEGORY))
      assert(deleteUppCategory == Some(Values.UPP_DELETE_CATEGORY))
    }

    "get no category" in {
      val pmHint100 = new ProtocolMessage(1, UUID.randomUUID(), 100, "")
      val pmHint200 = new ProtocolMessage(1, UUID.randomUUID(), 200, "")
      val pmHint300 = new ProtocolMessage(1, UUID.randomUUID(), 300, "")
      val ctxt = JObject()
      val none1 = UPPHelper.getCategory(MessageEnvelope(pmHint100, ctxt))
      val none2 = UPPHelper.getCategory(MessageEnvelope(pmHint200, ctxt))
      val none3 = UPPHelper.getCategory(MessageEnvelope(pmHint300, ctxt))
      assert(none1 == None)
      assert(none2 == None)
      assert(none3 == None)
    }
  }

  "Create payload hash" must {
    "succeed to create payload hash" in {
      val payload = getDigest(Array(1.toByte))
      val pm = new ProtocolMessage(1, UUID.randomUUID(), 0, payload)
      pm.setSignature(org.bouncycastle.util.Strings.toByteArray("1111"))
      val customerId = UUID.randomUUID().toString
      val ctxt = JObject("customerId" -> JString(customerId))
      val envelop = MessageEnvelope(pm, ctxt)

      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray)
      val encoderPipeData = EncoderPipeData(Vector(customerRecord), Vector.empty[JValue])
      val payloadHash = UPPHelper.createPayloadHash(envelop, encoderPipeData)
      assert(payloadHash.isSuccess)
      assert(payloadHash.get == payload)
    }

    "failed to create payload hash - payload is invalid" in {
      val payload = 1
      val pm = new ProtocolMessage(1, UUID.randomUUID(), 0, payload)
      pm.setSignature(org.bouncycastle.util.Strings.toByteArray("1111"))
      val customerId = UUID.randomUUID().toString
      val ctxt = JObject("customerId" -> JString(customerId))
      val envelop = MessageEnvelope(pm, ctxt)

      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray)
      val encoderPipeData = EncoderPipeData(Vector(customerRecord), Vector.empty[JValue])
      val payloadHash = UPPHelper.createPayloadHash(envelop, encoderPipeData)
      assert(payloadHash.isFailure)
      payloadHash.failed.foreach { e =>
        assert(e.getMessage == s"Error building payload | Payload is not valid: ${payload}")
      }
    }

    "fail to create payload hash - messageEnvelop is null" in {
      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray)
      val encoderPipeData = EncoderPipeData(Vector(customerRecord), Vector.empty[JValue])
      val payloadHash = UPPHelper.createPayloadHash(null, encoderPipeData)
      assert(payloadHash.isFailure)
      payloadHash.failed.foreach { e =>
        assert(e.getMessage == "Payload not found or is empty")
      }
    }

    "fail to create payload hash - ubirch pack is null" in {
      val customerId = UUID.randomUUID().toString
      val ctxt = JObject("customerId" -> JString(customerId))
      val envelop = MessageEnvelope(null, ctxt)

      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray)
      val encoderPipeData = EncoderPipeData(Vector(customerRecord), Vector.empty[JValue])
      val payloadHash = UPPHelper.createPayloadHash(envelop, encoderPipeData)
      assert(payloadHash.isFailure)
      payloadHash.failed.foreach { e =>
        assert(e.getMessage == "Payload not found or is empty")
      }
    }

    "fail to create payload hash - payload is null" in {
      val pm = new ProtocolMessage(1, UUID.randomUUID(), 0, null)
      pm.setSignature(org.bouncycastle.util.Strings.toByteArray("1111"))
      val customerId = UUID.randomUUID().toString
      val ctxt = JObject("customerId" -> JString(customerId))
      val envelop = MessageEnvelope(pm, ctxt)

      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray)
      val encoderPipeData = EncoderPipeData(Vector(customerRecord), Vector.empty[JValue])
      val payloadHash = UPPHelper.createPayloadHash(envelop, encoderPipeData)
      assert(payloadHash.isFailure)
      payloadHash.failed.foreach { e =>
        assert(e.getMessage == "Payload not found or is empty")
      }
    }
  }

  "Create Signature Lookup Key" must {
    "succeed to create signature lookup key" in {
      val payload = getDigest(Array(1.toByte))
      val pm = new ProtocolMessage(1, UUID.randomUUID(), 0, payload)
      val signature = org.bouncycastle.util.Strings.toByteArray("1111")
      pm.setSignature(signature)
      val customerId = UUID.randomUUID().toString
      val ctxt = JObject("customerId" -> JString(customerId))
      val envelop = MessageEnvelope(pm, ctxt)

      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray)
      val encoderPipeData = EncoderPipeData(Vector(customerRecord), Vector.empty[JValue])

      val signatureLookupKey = UPPHelper.createSignatureLookupKey(payload.toString, envelop, encoderPipeData, Values.UPP_CATEGORY)

      val lookUpKeys = Seq(LookupKey(Values.SIGNATURE, Values.UPP_CATEGORY, payload.toString.asKey, Seq(org.bouncycastle.util.encoders.Base64.toBase64String(signature).asValue)).categoryAsKeyLabel.nameAsValueLabelForAll)
      assert(signatureLookupKey.isSuccess)
      assert(signatureLookupKey.get == lookUpKeys)
    }

    "signature becomes empty - UPP is null" in {
      val payload = getDigest(Array(1.toByte))
      val customerId = UUID.randomUUID().toString
      val ctxt = JObject("customerId" -> JString(customerId))
      val envelop = MessageEnvelope(null, ctxt)

      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray)
      val encoderPipeData = EncoderPipeData(Vector(customerRecord), Vector.empty[JValue])

      val signatureLookupKey = UPPHelper.createSignatureLookupKey(payload.toString, envelop, encoderPipeData, Values.UPP_CATEGORY)

      assert(signatureLookupKey.isSuccess)
      assert(signatureLookupKey.get == Seq.empty[String])
    }

    "signature becomes empty - signature is null" in {
      val payload = getDigest(Array(1.toByte))
      val pm = new ProtocolMessage(1, UUID.randomUUID(), 0, payload)
      pm.setSignature(null)
      val customerId = UUID.randomUUID().toString
      val ctxt = JObject("customerId" -> JString(customerId))
      val envelop = MessageEnvelope(pm, ctxt)

      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray)
      val encoderPipeData = EncoderPipeData(Vector(customerRecord), Vector.empty[JValue])

      val signatureLookupKey = UPPHelper.createSignatureLookupKey(payload.toString, envelop, encoderPipeData, Values.UPP_CATEGORY)

      assert(signatureLookupKey.isSuccess)
      assert(signatureLookupKey.get == Seq.empty[String])
    }
  }

  "Create Device Lookup Key" must {
    "succeed to create device lookup key" in {
      val payload = getDigest(Array(1.toByte))
      val pm = new ProtocolMessage(1, UUID.randomUUID(), 0, payload)
      val deviceId = UUID.randomUUID()
      pm.setUUID(deviceId)
      val customerId = UUID.randomUUID().toString
      val ctxt = JObject("customerId" -> JString(customerId))
      val envelop = MessageEnvelope(pm, ctxt)

      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray)
      val encoderPipeData = EncoderPipeData(Vector(customerRecord), Vector.empty[JValue])

      val deviceLookupKey = UPPHelper.createDeviceLookupKey(payload.toString, envelop, encoderPipeData, Values.UPP_CATEGORY)

      val lookUpKeys = Seq(LookupKey(Values.DEVICE_ID, Values.DEVICE_CATEGORY, deviceId.toString.asKey, Seq(payload.toString.asValue)).withKeyLabel(Values.UPP_CATEGORY).categoryAsKeyLabel.addValueLabelForAll(Values.UPP_CATEGORY))
      assert(deviceLookupKey.isSuccess)
      assert(deviceLookupKey.get == lookUpKeys)
    }

    "device becomes empty - UPP is null" in {
      val payload = getDigest(Array(1.toByte))
      val customerId = UUID.randomUUID().toString
      val ctxt = JObject("customerId" -> JString(customerId))
      val envelop = MessageEnvelope(null, ctxt)

      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray)
      val encoderPipeData = EncoderPipeData(Vector(customerRecord), Vector.empty[JValue])

      val deviceLookupKey = UPPHelper.createDeviceLookupKey(payload.toString, envelop, encoderPipeData, Values.UPP_CATEGORY)

      assert(deviceLookupKey.isSuccess)
      assert(deviceLookupKey.get == Seq.empty[String])
    }

    "fail to create device lookup key - device is null" in {
      val payload = getDigest(Array(1.toByte))
      val pm = new ProtocolMessage(1, UUID.randomUUID(), 0, payload)
      pm.setUUID(null)
      val customerId = UUID.randomUUID().toString
      val ctxt = JObject("customerId" -> JString(customerId))
      val envelop = MessageEnvelope(pm, ctxt)

      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray)
      val encoderPipeData = EncoderPipeData(Vector(customerRecord), Vector.empty[JValue])

      val deviceLookupKey = UPPHelper.createDeviceLookupKey(payload.toString, envelop, encoderPipeData, Values.UPP_CATEGORY)

      assert(deviceLookupKey.isFailure)
      deviceLookupKey.failed.foreach { e =>
        assert(e.getMessage == "Error parsing deviceId [null] ")
      }
    }
  }

  "Create Chain Lookup Key" must {
    "succeed to create chain lookup key" in {
      val payload = getDigest(Array(1.toByte))
      val pm = new ProtocolMessage(1, UUID.randomUUID(), 0, payload)
      val chain = org.bouncycastle.util.Strings.toByteArray("this is my chain")
      pm.setChain(chain)
      val customerId = UUID.randomUUID().toString
      val ctxt = JObject("customerId" -> JString(customerId))
      val envelop = MessageEnvelope(pm, ctxt)

      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray)
      val encoderPipeData = EncoderPipeData(Vector(customerRecord), Vector.empty[JValue])

      val chainLookupKey = UPPHelper.createChainLookupKey(payload.toString, envelop, encoderPipeData, Values.UPP_CATEGORY)

      val lookUpKeys = Seq(LookupKey(Values.UPP_CHAIN, Values.CHAIN_CATEGORY, payload.toString.asKey, Seq(org.bouncycastle.util.encoders.Base64.toBase64String(chain).asValue)).withKeyLabel(Values.UPP_CATEGORY).categoryAsValueLabelForAll)
      assert(chainLookupKey.isSuccess)
      assert(chainLookupKey.get == lookUpKeys)
    }

    "chain becomes empty - UPP is null" in {
      val payload = getDigest(Array(1.toByte))
      val customerId = UUID.randomUUID().toString
      val ctxt = JObject("customerId" -> JString(customerId))
      val envelop = MessageEnvelope(null, ctxt)

      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray)
      val encoderPipeData = EncoderPipeData(Vector(customerRecord), Vector.empty[JValue])

      val chainLookupKey = UPPHelper.createChainLookupKey(payload.toString, envelop, encoderPipeData, Values.UPP_CATEGORY)

      assert(chainLookupKey.isSuccess)
      assert(chainLookupKey.get == Seq.empty[String])
    }

    "chain becomes empty - chain is null" in {
      val payload = getDigest(Array(1.toByte))
      val pm = new ProtocolMessage(1, UUID.randomUUID(), 0, payload)
      pm.setChain(null)
      val customerId = UUID.randomUUID().toString
      val ctxt = JObject("customerId" -> JString(customerId))
      val envelop = MessageEnvelope(pm, ctxt)

      val customerRecord = new ConsumerRecord("", 0, 0, 0, TimestampType.NO_TIMESTAMP_TYPE, 0L, 0, 0, "", Array.emptyByteArray)
      val encoderPipeData = EncoderPipeData(Vector(customerRecord), Vector.empty[JValue])

      val chainLookupKey = UPPHelper.createChainLookupKey(payload.toString, envelop, encoderPipeData, Values.UPP_CATEGORY)

      assert(chainLookupKey.isSuccess)
      assert(chainLookupKey.get == Seq.empty[String])
    }
  }
}
