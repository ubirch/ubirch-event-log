package com.ubirch.services.kafka.consumer

import com.ubirch.util.ConfigProperties
import org.apache.kafka.clients.consumer.{ ConsumerConfig, OffsetResetStrategy }
import org.apache.kafka.common.requests.IsolationLevel

object Configs {

  def apply[K, V](
      bootstrapServers: String = "localhost:9092",
      groupId: String,
      enableAutoCommit: Boolean = true,
      autoCommitInterval: Int = 1000,
      sessionTimeoutMs: Int = 10000,
      maxPartitionFetchBytes: Int = ConsumerConfig.DEFAULT_MAX_PARTITION_FETCH_BYTES,
      maxPollRecords: Int = 500,
      maxPollInterval: Int = 300000,
      maxMetaDataAge: Long = 300000,
      autoOffsetReset: OffsetResetStrategy = OffsetResetStrategy.LATEST,
      isolationLevel: IsolationLevel = IsolationLevel.READ_UNCOMMITTED
  ): ConfigProperties = {

    new ConfigProperties {
      override val props: Map[String, AnyRef] = {
        Map[String, AnyRef](
          ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> bootstrapServers,
          ConsumerConfig.GROUP_ID_CONFIG -> groupId,
          ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG -> enableAutoCommit.toString,
          ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG -> autoCommitInterval.toString,
          ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG -> sessionTimeoutMs.toString,
          ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG -> maxPartitionFetchBytes.toString,
          ConsumerConfig.MAX_POLL_RECORDS_CONFIG -> maxPollRecords.toString,
          ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG -> maxPollInterval.toString,
          ConsumerConfig.METADATA_MAX_AGE_CONFIG -> maxMetaDataAge.toString,
          ConsumerConfig.AUTO_OFFSET_RESET_CONFIG -> autoOffsetReset.toString.toLowerCase,
          ConsumerConfig.ISOLATION_LEVEL_CONFIG -> isolationLevel.toString.toLowerCase()
        )
      }
    }

  }
}
