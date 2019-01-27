package com.ubirch.services.kafka.producer

import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.common.serialization.Serializer

/**
  * Represents a simple definition for a kafka producer
  * @tparam K Represents the Key value
  * @tparam V Represents the Value
  */
trait KafkaProducerBase[K, V] {

  val producer: Producer[K, V]

  val keySerializer: Serializer[K]

  val valueSerializer: Serializer[V]

}

