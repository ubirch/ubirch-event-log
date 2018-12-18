package com.ubirch.services.kafka

import org.apache.kafka.clients.consumer.ConsumerRecord

trait WithConsumerRecordsExecutor[K, V, R] {

  val executor: Executor[ConsumerRecord[K, V], R]

}