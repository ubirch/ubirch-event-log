package com.ubirch.chainer

import com.ubirch.chainer.services.ChainerServiceBinder
import com.ubirch.kafka.consumer.{ All, StringConsumer }
import com.ubirch.util.Boot

import scala.language.postfixOps

/**
  * Represents an EventLog Chainer Service.
  */

object Service extends Boot(ChainerServiceBinder.modules) {

  def main(args: Array[String]): Unit = {

    val consumer: StringConsumer = get[StringConsumer]

    consumer.setConsumptionStrategy(All)

    consumer.start()

  }

}
