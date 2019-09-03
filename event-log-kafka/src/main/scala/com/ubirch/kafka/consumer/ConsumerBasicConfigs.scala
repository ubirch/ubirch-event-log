package com.ubirch.kafka.consumer

import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.common.serialization.Deserializer

trait ConsumerBasicConfigs[K, V] {

  def consumerTopics: Set[String]

  def consumerBootstrapServers: String

  def consumerGroupId: String

  def consumerMaxPollRecords: Int

  def consumerGracefulTimeout: Int

  def keyDeserializer: Deserializer[K]

  def valueDeserializer: Deserializer[V]

  def consumerConfigs = Configs(
    bootstrapServers = consumerBootstrapServers,
    groupId = consumerGroupId,
    enableAutoCommit = false,
    autoOffsetReset = OffsetResetStrategy.EARLIEST,
    maxPollRecords = consumerMaxPollRecords
  )

}
