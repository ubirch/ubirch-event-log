package com.ubirch.services.kafka.producer

import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.common.serialization.Serializer

trait KafkaProducerBase[K, V] {

  val producer: Producer[K, V]

  val keySerializer: Serializer[K]

  val valueSerializer: Serializer[V]

}

