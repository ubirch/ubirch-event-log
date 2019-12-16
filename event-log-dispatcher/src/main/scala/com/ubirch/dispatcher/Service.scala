package com.ubirch.dispatcher

import com.ubirch.dispatcher.services.DispatcherServiceBinder
import com.ubirch.kafka.consumer.{ All, StringConsumer }
import com.ubirch.util.Boot

import scala.language.postfixOps

/**
  * Represents an the Kafka Dispatching boot object.
  */
object Service extends Boot(DispatcherServiceBinder.modules) {

  def main(args: Array[String]): Unit = * {

    val consumer = get[StringConsumer]
    consumer.setConsumptionStrategy(All)

    consumer.start()

  }

}
