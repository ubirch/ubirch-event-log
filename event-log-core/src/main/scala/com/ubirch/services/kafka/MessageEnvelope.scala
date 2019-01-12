package com.ubirch.services.kafka

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.Header
import org.apache.kafka.common.header.internals.RecordHeader

import scala.collection.JavaConverters._

// errors -> { id, timestamp, error={}, signature }, log events -> {id, timestamp, event={}, signature }

case class MessageEnvelope[T](payload: T, headers: Map[String, String])

object MessageEnvelope {
  def apply[T](payload: T): MessageEnvelope[T] = {
    MessageEnvelope(payload, Map.empty)
  }

  def headersToMap[T](consumerRecord: ConsumerRecord[String, T]) = {
    consumerRecord.headers().asScala.map(h => h.key() -> new String(h.value())).toMap
  }

  def fromRecord[T](consumerRecord: ConsumerRecord[String, T]): MessageEnvelope[T] = {
    MessageEnvelope(
      consumerRecord.value(),
      headersToMap(consumerRecord)
    )
  }

  def toRecord[T](topic: String, key: String, messageEnvelope: MessageEnvelope[T]): ProducerRecord[String, T] = {
    val kafkaHeaders: Iterable[Header] = messageEnvelope.headers.map {
      case (k: String, v: Any) => new RecordHeader(k, v.getBytes)
    }
    new ProducerRecord[String, T](topic, null, key, messageEnvelope.payload, kafkaHeaders.asJava)
  }
}
