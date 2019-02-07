package com.ubirch.process

import com.ubirch.services.kafka.producer.Reporter
import org.apache.kafka.clients.consumer.ConsumerRecord

/**
  * A convenience type that holds an executor, an function that knows what to do
  * in case of an error and a reporter that can be used to send out messages to Kafka
  * reporting the errors.
  * @tparam K Represents the Key value for the ConsumerRecord
  * @tparam V Represents the Value for the ConsumerRecord
  * @tparam R Represents the Result type for the execution of the executors pipeline.
  * @tparam ER Represents the Exception Result type that is returned back
  *            when having handled the exceptions.
  */
trait WithConsumerRecordsExecutor[K, V, R, ER] {

  val executor: Executor[ConsumerRecord[K, V], R]

  val executorExceptionHandler: Exception => ER

  val reporter: Reporter

}
