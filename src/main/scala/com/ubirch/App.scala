package com.ubirch

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.services.execution.Execution
import com.ubirch.services.kafka._
import com.ubirch.util.Boot

object App extends Boot with LazyLogging with Execution {

  def main(args: Array[String]): Unit = {

    val configs = Configs(groupId = "my_group_id")

    val wrapper = new Wrapper
    val nonEmpty = new FilterEmpty
    val logger = new Logger

    val executor = wrapper andThen nonEmpty andThen logger

    val consumer = new StringConsumer(
      "test",
      configs,
      "test_thread",
      Option(executor))

    consumer.startPolling()

  }

}
