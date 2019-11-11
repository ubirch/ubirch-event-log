package com.ubirch.kafka.consumer

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.clients.consumer.{ ConsumerConfig, OffsetResetStrategy }
import org.apache.kafka.common.serialization.Deserializer

trait WithDeserializers[K, V] {
  def keyDeserializer: Deserializer[K]
  def valueDeserializer: Deserializer[V]
}

trait ConsumerBasicConfigs extends LazyLogging {

  def consumerTopics: Set[String]

  def consumerBootstrapServers: String

  def consumerGroupId: String

  def consumerMaxPollRecords: Int

  def consumerGracefulTimeout: Int

  def consumerFetchMaxBytesConfig: Int = ConsumerConfig.DEFAULT_FETCH_MAX_BYTES

  def consumerMaxPartitionFetchBytesConfig: Int = ConsumerConfig.DEFAULT_MAX_PARTITION_FETCH_BYTES

  def consumerReconnectBackoffMsConfig: Long

  def consumerReconnectBackoffMaxMsConfig: Long

  def consumerConfigs = {
    val cfs = Configs(
      bootstrapServers = consumerBootstrapServers,
      groupId = consumerGroupId,
      enableAutoCommit = false,
      autoOffsetReset = OffsetResetStrategy.EARLIEST,
      maxPollRecords = consumerMaxPollRecords,
      fetchMaxBytesConfig = consumerFetchMaxBytesConfig,
      maxPartitionFetchBytesConfig = consumerMaxPartitionFetchBytesConfig,
      reconnectBackoffMsConfig = consumerReconnectBackoffMsConfig,
      reconnectBackoffMaxMsConfig = consumerReconnectBackoffMaxMsConfig
    )

    logger.info(cfs.props.toString)

    cfs
  }

}
