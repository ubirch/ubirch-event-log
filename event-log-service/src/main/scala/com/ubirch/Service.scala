package com.ubirch

import com.ubirch.services.kafka.consumer.StringConsumer
import com.ubirch.util.Boot

/**
  * Represents an Event Log Service.
  * It starts an String Consumer that in turn starts all the rest of the
  * needed components, such as all the core components, executors, reporters, etc.
  */
object Service extends Boot {

  def main(args: Array[String]): Unit = {

    val consumer = get[StringConsumer]

    consumer.startPolling()

  }

}
