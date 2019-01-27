package com.ubirch.util

import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.Header
import org.apache.kafka.common.header.internals.RecordHeader

import scala.collection.JavaConverters._

/**
  * Helper that contains useful functions related to the ProducerRecord type.
  */
object ProducerRecordHelper {

  def toRecord[T](topic: String, key: String, payload: T, headers: Map[String, String]): ProducerRecord[String, T] = {
    val kafkaHeaders: Iterable[Header] = headers.map {
      case (k: String, v: Any) => new RecordHeader(k, v.getBytes)
    }

    new ProducerRecord[String, T](topic, null, key, payload, kafkaHeaders.asJava)
  }

}
