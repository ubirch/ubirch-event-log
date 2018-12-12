package com.ubirch.services.kafka

import org.apache.kafka.common.serialization.{ Deserializer, StringDeserializer }

abstract class AbstractStringConsumer[R](name: String)
    extends AbstractConsumer[String, String, R](name) {

  override val keyDeserializer: Deserializer[String] = new StringDeserializer()
  override val valueDeserializer: Deserializer[String] = new StringDeserializer()

}