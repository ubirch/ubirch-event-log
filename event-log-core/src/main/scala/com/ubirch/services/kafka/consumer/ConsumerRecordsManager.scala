package com.ubirch.services.kafka.consumer

import java.util.UUID

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.kafka.consumer.ConsumerRecordsController
import com.ubirch.process.WithConsumerRecordsExecutor
import com.ubirch.util.UUIDHelper
import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.concurrent.{ ExecutionContext, Future }

/**
  * Represents a combination between the executors and the processing
  * to be plugged in into the consumer.
  * @param ec Represents the execution context for async. computations.
  * @tparam K Represents the type of the Key for the consumer.
  * @tparam V Represents the type of the Value for the consumer.
  */
abstract class ConsumerRecordsManager[K, V](implicit ec: ExecutionContext)
  extends ConsumerRecordsController[K, V]
  with WithConsumerRecordsExecutor[K, V]
  with LazyLogging {

  def uuid: UUID = UUIDHelper.timeBasedUUID

  override def process(consumerRecords: Vector[ConsumerRecord[K, V]]): Future[A] = {
    try {
      executor(consumerRecords).recoverWith(executorExceptionHandler)
    } catch {
      executorExceptionHandler
    }
  }
}
