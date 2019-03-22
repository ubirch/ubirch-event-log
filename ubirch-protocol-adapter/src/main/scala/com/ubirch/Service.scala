package com.ubirch

import com.ubirch.services.AdapterServiceBinder
import com.ubirch.services.kafka.consumer.MessageEnvelopeConsumer
import com.ubirch.util.Boot

import scala.concurrent.duration._
import scala.language.postfixOps

object Service extends Boot(AdapterServiceBinder.modules) {

  def main(args: Array[String]): Unit = {

    val consumer = get[MessageEnvelopeConsumer]

    consumer.setDelaySingleRecord(500 micro)
    consumer.setDelayRecords(10 millis)

    consumer.start()

  }

}
