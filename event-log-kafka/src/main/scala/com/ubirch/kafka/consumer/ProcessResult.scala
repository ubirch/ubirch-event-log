package com.ubirch.kafka.consumer

import java.util.UUID

import org.apache.kafka.clients.consumer.ConsumerRecord

/**
  * Represents the result that is expected result for the consumption.
  * This is helpful to return the consumer record and an identifiable record.
  * This type is usually extended to support customized data.
  *
  * @tparam K Represents the type of the Key for the consumer.
  * @tparam V Represents the type of the Value for the consumer.
  */
trait ProcessResult[K, V] {

  val id: UUID

  val consumerRecords: Vector[ConsumerRecord[K, V]]

}
