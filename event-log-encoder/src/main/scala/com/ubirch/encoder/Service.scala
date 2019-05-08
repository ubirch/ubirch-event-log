package com.ubirch.encoder

import com.ubirch.encoder.services.EncoderServiceBinder
import com.ubirch.kafka.consumer.BytesConsumer
import com.ubirch.util.Boot

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Represents an Encoder boot object.
  */
object Service extends Boot(EncoderServiceBinder.modules) {

  def main(args: Array[String]): Unit = {

    val consumer = get[BytesConsumer]

    consumer.setDelaySingleRecord(500 micro)
    consumer.setDelayRecords(10 millis)

    consumer.start()

  }

}
