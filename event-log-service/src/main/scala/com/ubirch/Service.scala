package com.ubirch

import com.ubirch.services.kafka.consumer.StringConsumer
import com.ubirch.util.Boot

import scala.concurrent.duration._
import scala.language.postfixOps

/**
  * Represents an Event Log Service.
  * It starts an String Consumer that in turn starts all the rest of the
  * needed components, such as all the core components, executors, reporters, etc.
  */
object Service extends Boot {

  def main(args: Array[String]): Unit = {

    val consumer = get[StringConsumer]

    //Adding these configs to the consumer makes it add a back-off/back-pressure
    //strategy to the storage functions
    //setDelaySingleRecord adds a pause to the single record processing.
    //setDelayRecords adds a pause to the whole polled records processing.
    //
    //if both are 0 millis, no delay is applied.

    consumer.setDelaySingleRecord(500 micro)
    consumer.setDelayRecords(10 millis)

    consumer.start()

  }

}
