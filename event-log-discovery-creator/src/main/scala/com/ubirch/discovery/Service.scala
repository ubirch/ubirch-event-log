package com.ubirch.discovery

import com.ubirch.discovery.services.DiscoveryServiceBinder
import com.ubirch.kafka.express.ExpressKafka
import com.ubirch.util.Boot

/**
  * Represents an the Kafka Discovery boot object.
  */
object Service extends Boot(DiscoveryServiceBinder.modules) {

  def main(args: Array[String]): Unit = * {

    val expressKafka = get[ExpressKafka[String, String, Unit]]

    expressKafka.consumption.startWithExitControl()

  }

}
