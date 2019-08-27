package com.ubirch.services.kafka.consumer

import com.typesafe.config.Config
import com.ubirch.ConfPaths.ConsumerConfPaths
import com.ubirch.kafka.consumer.Configs
import com.ubirch.kafka.util.ConfigProperties
import com.ubirch.util.{ URLsHelper, UUIDHelper }
import org.apache.kafka.clients.consumer.OffsetResetStrategy

trait ConsumerCreator extends ConsumerConfPaths {

  def config: Config

  def gracefulTimeout: Int = config.getInt(GRACEFUL_TIMEOUT_PATH)

  def topics: Set[String] = config.getString(TOPIC_PATH).split(",").toSet.filter(_.nonEmpty).map(_.trim)

  def maxPollRecords: Int = config.getInt(MAX_POLL_RECORDS)

  def metricsSubNamespace: String = config.getString(METRICS_SUB_NAMESPACE)

  def bootstrapServers: String = URLsHelper.passThruWithCheck(config.getString(BOOTSTRAP_SERVERS))

  def fetchMaxBytesConfig: Int = config.getInt(FETCH_MAX_BYTES_CONFIG)

  def maxPartitionFetchBytesConfig: Int = config.getInt(MAX_PARTITION_FETCH_BYTES_CONFIG)

  def configs: ConfigProperties = Configs(
    bootstrapServers = bootstrapServers,
    groupId = groupId,
    enableAutoCommit = false,
    autoOffsetReset = OffsetResetStrategy.EARLIEST,
    maxPollRecords = maxPollRecords,
    fetchMaxBytesConfig = fetchMaxBytesConfig,
    maxPartitionFetchBytesConfig = maxPartitionFetchBytesConfig
  )

  def groupId: String = {
    val gid = config.getString(GROUP_ID_PATH)
    if (gid.isEmpty) groupIdOnEmpty + "_" + UUIDHelper.randomUUID
    else gid
  }

  def groupIdOnEmpty: String

}
