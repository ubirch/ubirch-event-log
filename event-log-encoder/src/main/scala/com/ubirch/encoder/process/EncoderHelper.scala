package com.ubirch.encoder.process

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.encoder.services.kafka.consumer.EncoderPipeData
import com.ubirch.encoder.util.Exceptions.EventLogFromConsumerRecordException
import com.ubirch.kafka.MessageEnvelope
import com.ubirch.models.LookupKey.Helpers
import org.json4s.jackson.JsonMethods.fromJsonNode
import com.ubirch.models.{ LookupKey, Values }
import com.ubirch.encoder.util.EncoderJsonSupport._

import scala.util.{ Failure, Success, Try }

object EncoderHelper extends LazyLogging {
  def createPayloadHash(messageEnvelope: MessageEnvelope, encoderPipeData: EncoderPipeData): Try[String] = Try {
    val payloadJsNode = Option(messageEnvelope)
      .flatMap(x => Option(x.ubirchPacket))
      .flatMap(x => Option(x.getPayload))
      .getOrElse(throw EventLogFromConsumerRecordException("Payload not found or is empty", encoderPipeData))

    fromJsonNode(payloadJsNode)
      .extractOpt[String]
      .filter(_.nonEmpty)
      .map { x =>
        (Try(org.bouncycastle.util.encoders.Base64.decode(x)), x)
      } match {
        //We want to ensure that the payload is OK
        case Some((Success(value), s)) if value.length <= 100 => s
        case Some((Success(_), s)) =>
          throw EventLogFromConsumerRecordException("Error building payload | Payload length is not valid: " + s, encoderPipeData)
        case Some((Failure(_), s)) =>
          throw EventLogFromConsumerRecordException("Error building payload | Payload is not valid: " + s, encoderPipeData)
        case None =>
          throw EventLogFromConsumerRecordException("Error building payload | Payload is empty", encoderPipeData)
      }
  }

  def createSignatureLookupKey(payloadHash: String, messageEnvelope: MessageEnvelope, encoderPipeData: EncoderPipeData, category: String): Try[Seq[LookupKey]] = Try {
    val maybeSignature = Option(messageEnvelope.ubirchPacket)
      .flatMap(x => Option(x.getSignature))
      .map(x => Try(org.bouncycastle.util.encoders.Base64.toBase64String(x)))
      .flatMap {
        case Success(value) if value.nonEmpty => Some(value)
        case Success(_) => None
        case Failure(e) =>
          logger.error("Error Parsing Into Event Log [Signature]: {}", e.getMessage)
          throw EventLogFromConsumerRecordException(s"Error parsing signature [${e.getMessage}] ", encoderPipeData)
      }

    maybeSignature.map { x =>
      LookupKey(
        name = Values.SIGNATURE,
        category = category,
        key = payloadHash.asKey,
        value = Seq(x.asValue)
      ).categoryAsKeyLabel
        .nameAsValueLabelForAll
    }.toSeq
  }

  def createDeviceLookupKey(payloadHash: String, messageEnvelope: MessageEnvelope, encoderPipeData: EncoderPipeData, category: String): Try[Seq[LookupKey]] = Try {
    val maybeDevice = Option(messageEnvelope.ubirchPacket)
      .flatMap(x => Option(x).map(_.getUUID))
      .map(x => Try(x.toString))
      .flatMap {
        case Success(value) if value.nonEmpty => Some(value)
        case Success(_) => None
        case Failure(e) =>
          logger.error("Error Parsing Into Event Log [deviceId]: {}", e.getMessage)
          throw EventLogFromConsumerRecordException(s"Error parsing deviceId [${e.getMessage}] ", encoderPipeData)
      }

    maybeDevice.map { x =>
      LookupKey(
        name = Values.DEVICE_ID,
        category = Values.DEVICE_CATEGORY,
        key = x.asKey,
        value = Seq(payloadHash.asValue)
      ).categoryAsKeyLabel
        .addValueLabelForAll(category)
    }.toSeq
  }

  def createChainLookupKey(payloadHash: String, messageEnvelope: MessageEnvelope, encoderPipeData: EncoderPipeData, category: String): Try[Seq[LookupKey]] = Try {
    val maybeChain = Option(messageEnvelope.ubirchPacket)
      .flatMap(x => Option(x.getChain))
      .map(x => Try(org.bouncycastle.util.encoders.Base64.toBase64String(x)))
      .flatMap {
        case Success(value) if value.nonEmpty => Some(value)
        case Success(_) => None
        case Failure(e) =>
          logger.error("Error Parsing Into Event Log [Chain]: {}", e.getMessage)
          throw EventLogFromConsumerRecordException(s"Error parsing chain [${e.getMessage}] ", encoderPipeData)
      }
    maybeChain.map { x =>
      LookupKey(
        name = Values.UPP_CHAIN,
        category = Values.CHAIN_CATEGORY,
        key = payloadHash.asKey,
        value = Seq(x.asValue)
      ).withKeyLabel(category)
        .categoryAsValueLabelForAll
    }.toSeq
  }
}
