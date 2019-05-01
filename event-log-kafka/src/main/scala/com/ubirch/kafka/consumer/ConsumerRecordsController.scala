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

  type A <: ProcessResult[K, V]

  def process(consumerRecord: Vector[ConsumerRecord[K, V]]): Future[A]

}

trait StringConsumerRecordsController extends ConsumerRecordsController[String, String]

sealed trait ConsumptionStrategy

case object All extends ConsumptionStrategy
case object One extends ConsumptionStrategy
