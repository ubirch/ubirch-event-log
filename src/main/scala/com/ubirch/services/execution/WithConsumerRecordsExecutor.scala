package com.ubirch.services.execution

import com.ubirch.process.Executor
import com.ubirch.services.kafka.producer.Reporter
import org.apache.kafka.clients.consumer.ConsumerRecord

trait WithConsumerRecordsExecutor[K, V, R] {

  val executor: Executor[ConsumerRecord[K, V], R]

  val executorExceptionHandler: Exception ⇒ Unit

  val reporter: Reporter

}
