package com.ubirch.adapter.models

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.adapter.ServiceTraits
import com.ubirch.adapter.services.kafka.consumer.MessageEnvelopePipeData
import com.ubirch.adapter.util.AdapterJsonSupport
import com.ubirch.adapter.util.Exceptions.EventLogFromConsumerRecordException
import com.ubirch.kafka.MessageEnvelope
import com.ubirch.models.{ EventLog, LookupKey }
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.util.Ignore
import org.apache.kafka.clients.producer.ProducerRecord
import org.json4s.JValue

import scala.util.{ Failure, Success, Try }

object Encodings extends LazyLogging {

  import AdapterJsonSupport._
  import org.json4s.jackson.JsonMethods._

  val CUSTOMER_ID_FIELD = "customerId"

  def UPA(messageEnvelopePipeData: MessageEnvelopePipeData): PartialFunction[JValue, MessageEnvelopePipeData] = {

    case jv if Try(AdapterJsonSupport.FromJson[MessageEnvelope](jv).get).isSuccess =>

      val messageEnvelope: MessageEnvelope = AdapterJsonSupport.FromJson[MessageEnvelope](jv).get

      val jValueCustomerId = messageEnvelope.context \\ CUSTOMER_ID_FIELD
      val customerId = jValueCustomerId.extractOpt[String]
        .filter(_.nonEmpty)
        .getOrElse {
          throw EventLogFromConsumerRecordException("No CustomerId found", messageEnvelopePipeData)
        }

      val payloadToJson = AdapterJsonSupport.ToJson[ProtocolMessage](messageEnvelope.ubirchPacket)

      val payload = payloadToJson.get

      val (eventLog, pr) = if (messageEnvelope.ubirchPacket.getHint == 0) {

        val payloadHash = fromJsonNode(messageEnvelope.ubirchPacket.getPayload)
          .extractOpt[String]
          .filter(_.nonEmpty)
          .getOrElse {
            throw EventLogFromConsumerRecordException("Payload not found or is empty", messageEnvelopePipeData)
          }

        val maybeSignature = Option(messageEnvelope.ubirchPacket)
          .flatMap(x => Option(x.getSignature))
          .map(x => Try(org.bouncycastle.util.encoders.Base64.toBase64String(x)))
          .flatMap {
            case Success(value) if value.nonEmpty => Some(value)
            case Success(_) => None
            case Failure(e) =>
              logger.error("Error Parsing Into Event Log [Signature]: {}", e.getMessage)
              throw EventLogFromConsumerRecordException(s"Error parsing signature [${e.getMessage}] ", messageEnvelopePipeData)
          }

        //TODO: ADD THE QUERY TYPES TO UTILS OR CORE
        val maybeLookupKeys = maybeSignature.map { x =>
          Seq(
            LookupKey(
              "signature",
              ServiceTraits.ADAPTER_CATEGORY,
              payloadHash,
              Seq(x)
            )
          )
        }.getOrElse(Nil)

        val el = EventLog("EventLogFromConsumerRecord", ServiceTraits.ADAPTER_CATEGORY, payload)
          .withLookupKeys(maybeLookupKeys)
          .withCustomerId(customerId)
          .withNewId(payloadHash)

        (el, None)

      } else {
        val el = EventLog("EventLogFromConsumerRecord", ServiceTraits.ADAPTER_CATEGORY, payload).withCustomerId(customerId)
        (el, Some(Ignore[ProducerRecord[String, String]]()))
      }

      messageEnvelopePipeData.copy(eventLog = Some(eventLog), producerRecord = pr)

  }

  def PublichBlockchain(messageEnvelopePipeData: MessageEnvelopePipeData): PartialFunction[JValue, MessageEnvelopePipeData] = {
    case jv if Try(AdapterJsonSupport.FromJson[BlockchainResponse](jv).get).isSuccess =>

      val blockchainResponse: BlockchainResponse = AdapterJsonSupport.FromJson[BlockchainResponse](jv).get

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

      messageEnvelopePipeData.copy(eventLog = Some(eventLog), producerRecord = None)

  }

}
