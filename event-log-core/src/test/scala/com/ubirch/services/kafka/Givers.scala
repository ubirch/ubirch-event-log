package com.ubirch.services.kafka

import java.util.UUID

import com.ubirch.util.UUIDHelper

object PortGiver {

  private var kafkaPort = 9092

  def giveMeKafkaPort: Int = {
    this.synchronized {
      kafkaPort = kafkaPort + 1
      kafkaPort
    }
  }

  private var zooKeeperPort = 6001

  def giveMeZookeeperPort: Int = {
    this.synchronized {
      zooKeeperPort = zooKeeperPort + 1
      zooKeeperPort
    }
  }

}

object NameGiver {

  import UUIDHelper._

  def giveMeATopicName = "com.ubirch.eventlog_" + randomUUID

  def giveMeAnErrorTopicName = "com.ubirch.eventlog.error_" + randomUUID

  def giveMeAThreadName = "my_eventlog_thread" + randomUUID

}
