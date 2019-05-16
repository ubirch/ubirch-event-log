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

  def bootstrapServers: String = URLsHelper.passThruWithCheck(config.getString(BOOTSTRAP_SERVERS))

  def configs: ConfigProperties = Configs(
    bootstrapServers = bootstrapServers,
    groupId = groupId,
    enableAutoCommit = false,
    autoOffsetReset = OffsetResetStrategy.EARLIEST,
    maxPollRecords = maxPollRecords
  )

  def groupId: String = {
    val gid = config.getString(GROUP_ID_PATH)
    if (gid.isEmpty) groupIdOnEmpty + "_" + UUIDHelper.randomUUID
    else gid
  }

  def groupIdOnEmpty: String

}
