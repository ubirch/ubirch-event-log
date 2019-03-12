package com.ubirch.util

import java.util.UUID

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

  def randomUUID: UUID = UUID.randomUUID()

  def giveMeATopicName: String = "com.ubirch.eventlog_" + randomUUID

  def giveMeAnErrorTopicName: String = "com.ubirch.eventlog.error_" + randomUUID

  def giveMeAThreadName: String = "my_eventlog_thread" + randomUUID

}
