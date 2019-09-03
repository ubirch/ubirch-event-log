package com.ubirch.kafka.producer

import org.apache.kafka.common.serialization.Serializer

trait ProducerBasicConfigs[K, V] {

  def producerBootstrapServers: String

  def lingerMs: Int

  def keySerializer: Serializer[K]

  def valueSerializer: Serializer[V]

  def producerConfigs = Configs(producerBootstrapServers, lingerMs = lingerMs)

}
