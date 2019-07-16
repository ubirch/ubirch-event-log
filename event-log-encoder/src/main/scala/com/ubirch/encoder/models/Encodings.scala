package com.ubirch.encoder.models

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.encoder.services.kafka.consumer.EncoderPipeData
import com.ubirch.encoder.util.EncoderJsonSupport
import com.ubirch.encoder.util.Exceptions.EventLogFromConsumerRecordException
import com.ubirch.kafka.MessageEnvelope
import com.ubirch.models.{ EventLog, LookupKey, Values }
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.util.Ignore
import org.apache.kafka.clients.producer.ProducerRecord
import org.json4s.JValue

import scala.util.{ Failure, Success, Try }

object Encodings extends LazyLogging {

  import EncoderJsonSupport._
  import org.json4s.jackson.JsonMethods._

  val CUSTOMER_ID_FIELD = "customerId"

  def UPP(encoderPipeData: EncoderPipeData): PartialFunction[JValue, EncoderPipeData] = {

    case jv if Try(EncoderJsonSupport.FromJson[MessageEnvelope](jv).get).isSuccess =>

      val messageEnvelope: MessageEnvelope = EncoderJsonSupport.FromJson[MessageEnvelope](jv).get

      val jValueCustomerId = messageEnvelope.context \\ CUSTOMER_ID_FIELD
      val customerId = jValueCustomerId.extractOpt[String]
        .filter(_.nonEmpty)
        .getOrElse {
          throw EventLogFromConsumerRecordException("No CustomerId found", encoderPipeData)
        }

      val payloadToJson = EncoderJsonSupport.ToJson[ProtocolMessage](messageEnvelope.ubirchPacket)

      val payload = payloadToJson.get

      val (eventLog, pr) = if (messageEnvelope.ubirchPacket.getHint == 0) {

        val payloadHash = fromJsonNode(messageEnvelope.ubirchPacket.getPayload)
          .extractOpt[String]
          .filter(_.nonEmpty)
          .getOrElse {
            throw EventLogFromConsumerRecordException("Payload not found or is empty", encoderPipeData)
          }

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

        val signatureLookupKey = maybeSignature.map { x =>
          LookupKey(
            name = Values.SIGNATURE,
            category = Values.UPP_CATEGORY,
            key = payloadHash,
            value = Seq(x)
          ).categoryAsKeyLabel
            .nameAsValueLabelForAll
        }.toSeq

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

        val deviceLookupKey = maybeDevice.map { x =>
          LookupKey(
            name = Values.DEVICE_ID,
            category = Values.DEVICE_CATEGORY,
            key = x,
            value = Seq(payloadHash)
          ).categoryAsKeyLabel
            .addValueLabelForAll(Values.UPP_CATEGORY)
        }.toSeq

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

        val chainLookupKey = maybeChain.map { x =>
          LookupKey(
            name = Values.UPP_CHAIN,
            category = Values.CHAIN_CATEGORY,
            key = payloadHash,
            value = Seq(x)
          ).withKeyLabel(Values.UPP_CATEGORY)
            .categoryAsValueLabelForAll
        }.toSeq

        val el = EventLog("upp-event-log-entry", Values.UPP_CATEGORY, payload)
          .withLookupKeys(signatureLookupKey ++ chainLookupKey ++ deviceLookupKey)
          .withCustomerId(customerId)
          .withRandomNonce
          .withNewId(payloadHash)

        logger.debug(s"Encoded Message: ${el.toJson}")

        (el, None)

      } else {
        val el = EventLog("EventLogFromConsumerRecord", Values.UPP_CATEGORY, payload).withCustomerId(customerId)
        (el, Some(Ignore[ProducerRecord[String, String]]()))
      }

      encoderPipeData.copy(eventLog = Some(eventLog), producerRecord = pr)

  }

  def PublichBlockchain(encoderPipeData: EncoderPipeData): PartialFunction[JValue, EncoderPipeData] = {
    case jv if Try(EncoderJsonSupport.FromJson[BlockchainResponse](jv).get).isSuccess =>

      val blockchainResponse: BlockchainResponse = EncoderJsonSupport.FromJson[BlockchainResponse](jv).get

      //The category of the tx event log has to be the name of the lookup key.
      val eventLog = EventLog("EventLogFromConsumerRecord", blockchainResponse.category, jv)
        .withCustomerId(Values.UBIRCH)
        .withNewId(blockchainResponse.txid)
        .withLookupKeys(Seq(
          LookupKey(
            name = blockchainResponse.category,
            category = Values.PUBLIC_CHAIN_CATEGORY,
            key = blockchainResponse.txid,
            value = Seq(blockchainResponse.message)
          ).categoryAsKeyLabel
            .addValueLabelForAll(Values.MASTER_TREE_CATEGORY)
        ))

      encoderPipeData.copy(eventLog = Some(eventLog), producerRecord = None)

  }

}
