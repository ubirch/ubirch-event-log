package com.ubirch.services.kafka

import java.nio.charset.StandardCharsets.UTF_8

import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.collection.JavaConverters._
import scala.collection.breakOut
import scala.language.implicitConversions

//This class is a temporal helper: Should be removed after
//express kafka has been upgraded.
case class EnrichedConsumerRecord[K, V](cr: ConsumerRecord[K, V]) extends AnyVal {

  def findHeader(key: String): Option[String] = {
    cr.headers()
      .asScala
      .find(h => h.key().toLowerCase == key.toLowerCase)
      .map(h => new String(h.value(), UTF_8))
  }

  def existsHeader(key: String): Boolean = findHeader(key).isDefined

  def headersScala: Map[String, String] = cr.headers()
    .asScala
    .map(h => h.key() -> new String(h.value(), UTF_8))(breakOut)

}

object EnrichedConsumerRecord {
  implicit def enrichedConsumerRecord[K, V](cr: ConsumerRecord[K, V]): EnrichedConsumerRecord[K, V] = EnrichedConsumerRecord[K, V](cr)
}
