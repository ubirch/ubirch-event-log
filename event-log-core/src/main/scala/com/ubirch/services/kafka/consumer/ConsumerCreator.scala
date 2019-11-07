package com.ubirch.services.kafka.consumer

import com.typesafe.config.Config
import com.ubirch.ConfPaths.ConsumerConfPaths
import com.ubirch.kafka.consumer.ConsumerBasicConfigs
import com.ubirch.util.{ URLsHelper, UUIDHelper }

trait ConsumerCreator extends ConsumerBasicConfigs with ConsumerConfPaths {

  def config: Config

  def metricsSubNamespace: String = config.getString(METRICS_SUB_NAMESPACE)

  def consumerTopics: Set[String] = config.getString(TOPIC_PATH).split(",").toSet.filter(_.nonEmpty).map(_.trim)

  def consumerBootstrapServers: String = URLsHelper.passThruWithCheck(config.getString(BOOTSTRAP_SERVERS))

  def consumerGroupId: String = {
    val gid = config.getString(GROUP_ID_PATH)
    if (gid.isEmpty) consumerGroupIdOnEmpty + "_" + UUIDHelper.randomUUID
    else gid
  }

  def consumerMaxPollRecords: Int = config.getInt(MAX_POLL_RECORDS)

  def consumerGracefulTimeout: Int = config.getInt(GRACEFUL_TIMEOUT_PATH)

  override def consumerFetchMaxBytesConfig: Int = config.getInt(FETCH_MAX_BYTES_CONFIG)

  override def consumerMaxPartitionFetchBytesConfig: Int = config.getInt(MAX_PARTITION_FETCH_BYTES_CONFIG)

  def consumerGroupIdOnEmpty: String

}
