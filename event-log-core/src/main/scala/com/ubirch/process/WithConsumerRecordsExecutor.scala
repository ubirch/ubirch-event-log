package com.ubirch.process

import com.ubirch.kafka.consumer.ProcessResult
import com.ubirch.services.kafka.producer.Reporter
import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.concurrent.Future

/**
  * A convenience type that holds an executor, an function that knows what to do
  * in case of an error and a reporter that can be used to send out messages to Kafka
  * reporting the errors.
  * @tparam K Represents the Key value for the ConsumerRecord
  * @tparam V Represents the Value for the ConsumerRecord
  * @tparam R Represents the Result type for the execution of the executors pipeline.
  */
trait WithConsumerRecordsExecutorBase[K, V, R] {

  type A <: R

  def executor: Executor[ConsumerRecord[K, V], Future[A]]

  def executorExceptionHandler: PartialFunction[Throwable, Future[A]]

  def reporter: Reporter

}

/**
  * A convenience type that holds an executor, an function that knows what to do
  * in case of an error and a reporter that can be used to send out messages to Kafka
  * reporting the errors.
  * @tparam K Represents the Key value for the ConsumerRecord
  * @tparam V Represents the Value for the ConsumerRecord
  */
trait WithConsumerRecordsExecutor[K, V] extends WithConsumerRecordsExecutorBase[K, V, ProcessResult[K, V]]

trait StringConsumerRecordsExecutor extends WithConsumerRecordsExecutor[String, String]
