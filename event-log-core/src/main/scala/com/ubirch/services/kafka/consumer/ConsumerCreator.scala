package com.ubirch.services.kafka.consumer

import com.typesafe.config.Config
import com.ubirch.ConfPaths.ConsumerConfPaths
import com.ubirch.kafka.consumer.ConsumerBasicConfigs
import com.ubirch.util.{ URLsHelper, UUIDHelper }

trait ConsumerCreator extends ConsumerBasicConfigs {

  def config: Config

  def metricsSubNamespace: String = config.getString(ConsumerConfPaths.METRICS_SUB_NAMESPACE)

  override def consumerTopics: Set[String] = config.getString(ConsumerConfPaths.TOPIC_PATH).split(",").toSet.filter(_.nonEmpty).map(_.trim)

  override def consumerBootstrapServers: String = URLsHelper.passThruWithCheck(config.getString(ConsumerConfPaths.BOOTSTRAP_SERVERS))

  override def consumerGroupId: String = {
    val gid = config.getString(ConsumerConfPaths.GROUP_ID_PATH)
    if (gid.isEmpty) consumerGroupIdOnEmpty + "_" + UUIDHelper.randomUUID
    else gid
  }

  override def consumerMaxPollRecords: Int = config.getInt(ConsumerConfPaths.MAX_POLL_RECORDS)

  override def consumerGracefulTimeout: Int = config.getInt(ConsumerConfPaths.GRACEFUL_TIMEOUT_PATH)

  override def consumerFetchMaxBytesConfig: Int = config.getInt(ConsumerConfPaths.FETCH_MAX_BYTES_CONFIG)

  override def consumerMaxPartitionFetchBytesConfig: Int = config.getInt(ConsumerConfPaths.MAX_PARTITION_FETCH_BYTES_CONFIG)

  override def consumerReconnectBackoffMsConfig: Long = config.getInt(ConsumerConfPaths.RECONNECT_BACKOFF_MS_CONFIG)

  override def consumerReconnectBackoffMaxMsConfig: Long = config.getInt(ConsumerConfPaths.RECONNECT_BACKOFF_MAX_MS_CONFIG)

  def consumerGroupIdOnEmpty: String

}
