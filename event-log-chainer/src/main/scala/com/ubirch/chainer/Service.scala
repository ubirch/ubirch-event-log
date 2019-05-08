package com.ubirch.chainer

import com.ubirch.chainer.services.ChainerServiceBinder
import com.ubirch.kafka.consumer.{ All, StringConsumer }
import com.ubirch.util.Boot

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Represents an EventLog Chainer Service.
  */

object Service extends Boot(ChainerServiceBinder.modules) {

  def main(args: Array[String]): Unit = {

    val consumer: StringConsumer = get[StringConsumer]

    consumer.setDelaySingleRecord(50 micro)
    consumer.setConsumptionStrategy(All)
    consumer.setDelayRecords(10 seconds)

    consumer.start()

  }

}
