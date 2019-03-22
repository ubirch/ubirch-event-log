package com.ubirch.services.kafka.consumer

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.kafka.consumer.ConsumerRecordsController
import com.ubirch.process.WithConsumerRecordsExecutor
import com.ubirch.util.UUIDHelper
import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.concurrent.{ ExecutionContext, Future }

abstract class ConsumerRecordsManager[K, V](implicit ec: ExecutionContext)
  extends ConsumerRecordsController[K, V]
  with WithConsumerRecordsExecutor[K, V]
  with LazyLogging {

  def uuid = UUIDHelper.timeBasedUUID

  override def process(consumerRecord: ConsumerRecord[K, V]): Future[A] = {
    try {
      executor(consumerRecord).recoverWith(executorExceptionHandler)
    } catch {
      executorExceptionHandler
    }
  }
}
