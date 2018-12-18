package com.ubirch

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.services.execution.Execution
import com.ubirch.services.kafka._
import com.ubirch.util.Boot

object App extends Boot with LazyLogging with Execution {

  def main(args: Array[String]): Unit = {

    val consumer = get[StringConsumer]

    consumer.startPolling()

  }

}
