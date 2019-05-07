package com.ubirch.dispatcher

import com.ubirch.dispatcher.services.DispatcherServiceBinder
import com.ubirch.kafka.consumer.StringConsumer
import com.ubirch.util.Boot

import scala.language.postfixOps

/**
  * Represents an the Kafka Dispatching boot object.
  */
object Service extends Boot(DispatcherServiceBinder.modules) {

  def main(args: Array[String]): Unit = {

    val consumer = get[StringConsumer]

    //consumer.setDelaySingleRecord(500 micro)
    //consumer.setDelayRecords(10 millis)

    consumer.start()

  }

}
