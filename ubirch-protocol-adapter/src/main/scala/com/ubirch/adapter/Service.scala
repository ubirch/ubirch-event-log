package com.ubirch.adapter

import com.ubirch.adapter.services.AdapterServiceBinder
import com.ubirch.kafka.consumer.BytesConsumer
import com.ubirch.util.Boot

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Represents an adapter boot object.
  */
object Service extends Boot(AdapterServiceBinder.modules) {

  def main(args: Array[String]): Unit = {

    val consumer = get[BytesConsumer]

    consumer.setDelaySingleRecord(500 micro)
    consumer.setDelayRecords(10 millis)

    consumer.start()

  }

}
