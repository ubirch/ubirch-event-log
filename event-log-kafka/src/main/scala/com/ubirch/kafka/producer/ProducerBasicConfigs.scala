package com.ubirch.kafka.producer

import org.apache.kafka.common.serialization.Serializer

trait WithSerializer[K, V] {
  def keySerializer: Serializer[K]
  def valueSerializer: Serializer[V]
}

trait ProducerBasicConfigs {

  def producerBootstrapServers: String

  def lingerMs: Int

  def producerConfigs = Configs(producerBootstrapServers, lingerMs = lingerMs)

}
