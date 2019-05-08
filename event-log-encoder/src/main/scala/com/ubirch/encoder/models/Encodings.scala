package com.ubirch.encoder.models

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.encoder.ServiceTraits
import com.ubirch.encoder.services.kafka.consumer.EncoderPipeData
import com.ubirch.encoder.util.EncoderJsonSupport
import com.ubirch.encoder.util.Exceptions.EventLogFromConsumerRecordException
import com.ubirch.kafka.MessageEnvelope
import com.ubirch.models.{ EventLog, LookupKey }
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.util.Ignore
import org.apache.kafka.clients.producer.ProducerRecord
import org.json4s.JValue

import scala.util.{ Failure, Success, Try }

object Encodings extends LazyLogging {

  import EncoderJsonSupport._
  import org.json4s.jackson.JsonMethods._

  val CUSTOMER_ID_FIELD = "customerId"

  def UPA(encoderPipeData: EncoderPipeData): PartialFunction[JValue, EncoderPipeData] = {

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

        //TODO: ADD THE QUERY TYPES TO UTILS OR CORE
        val maybeLookupKeys = maybeSignature.map { x =>
          Seq(
            LookupKey(
              "signature",
              ServiceTraits.ENCODER_CATEGORY,
              payloadHash,
              Seq(x)
            )
          )
        }.getOrElse(Nil)

        val el = EventLog("EventLogFromConsumerRecord", ServiceTraits.ENCODER_CATEGORY, payload)
          .withLookupKeys(maybeLookupKeys)
          .withCustomerId(customerId)
          .withRandomNonce
          .withNewId(payloadHash)

        (el, None)

      } else {
        val el = EventLog("EventLogFromConsumerRecord", ServiceTraits.ENCODER_CATEGORY, payload).withCustomerId(customerId)
        (el, Some(Ignore[ProducerRecord[String, String]]()))
      }

      encoderPipeData.copy(eventLog = Some(eventLog), producerRecord = pr)

  }

  def PublichBlockchain(encoderPipeData: EncoderPipeData): PartialFunction[JValue, EncoderPipeData] = {
    case jv if Try(EncoderJsonSupport.FromJson[BlockchainResponse](jv).get).isSuccess =>

      val blockchainResponse: BlockchainResponse = EncoderJsonSupport.FromJson[BlockchainResponse](jv).get

      val eventLog = EventLog("EventLogFromConsumerRecord", blockchainResponse.category, jv)
        .withNewId(blockchainResponse.txid)
        .withLookupKeys(Seq(
          LookupKey(
            "blockchain_tx_id",
            blockchainResponse.category,
            blockchainResponse.message,
            Seq(blockchainResponse.txid)
          )
        ))

      encoderPipeData.copy(eventLog = Some(eventLog), producerRecord = None)

  }

}
