package com.ubirch.services.kafka.consumer

import org.apache.kafka.clients.consumer.ConsumerRecord

trait WithConsumerRecordsExecutor[K, V, R] {

  val executor: Executor[ConsumerRecord[K, V], R]

  val reporter: Reporter

}
