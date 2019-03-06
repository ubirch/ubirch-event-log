package com.ubirch.kafka.consumer

import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.concurrent.Future

/**
  * Represents the the type that is actually processes the consumer records.
  * The consumer doesn't care about how it is processed, it can be with
  * Futures, Actors, as long as the result type matches.
  *
  * @tparam K Represents the type of the Key for the consumer.
  * @tparam V Represents the type of the Value for the consumer.
  */
trait ConsumerRecordsController[K, V] {

  def process[A >: ProcessResult[K, V]](consumerRecord: ConsumerRecord[K, V]): Future[A]

}
