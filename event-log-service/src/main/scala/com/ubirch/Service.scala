package com.ubirch

import com.ubirch.kafka.consumer.{ All, StringConsumer }
import com.ubirch.service.ExtServiceBinder
import com.ubirch.service.rest.RestService
import com.ubirch.services.ServiceBinder
import com.ubirch.util.Boot

import scala.language.postfixOps

/**
  * Represents an Event Log Service.
  * It starts an String Consumer that in turn starts all the rest of the
  * needed components, such as all the core components, executors, reporters, etc.
  */
object Service extends Boot(ServiceBinder.modules ++ ExtServiceBinder.modules) {

  def main(args: Array[String]): Unit = * {

    logger.info("Starting Rest")

    val restEndpoint = get[RestService]
    restEndpoint.start

    logger.info("Starting consumer")

    val consumer = get[StringConsumer]
    consumer.setConsumptionStrategy(All)

    //Adding these configs to the consumer makes it add a back-off/back-pressure
    //strategy to the storage functions
    //setDelaySingleRecord adds a pause to the single record processing.
    //setDelayRecords adds a pause to the whole polled records processing.
    //
    //if both are 0 millis, no delay is applied.

    //* cosmosdb related settings
    //  consumer.setDelaySingleRecord(500 micro)
    //  consumer.setDelayRecords(10 millis)

    consumer.startWithExitControl()

  }

}
