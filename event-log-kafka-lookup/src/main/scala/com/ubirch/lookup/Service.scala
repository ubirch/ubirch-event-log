package com.ubirch.lookup

import com.ubirch.kafka.consumer.StringConsumer
import com.ubirch.lookup.services.LookupServiceBinder
import com.ubirch.util.Boot

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Represents an the Kafka Lookup boot object.
  */
object Service extends Boot(LookupServiceBinder.modules) {

  def main(args: Array[String]): Unit = {

    val consumer = get[StringConsumer]

    //consumer.setDelaySingleRecord(500 micro)
    //consumer.setDelayRecords(10 millis)

    consumer.start()

  }

}
