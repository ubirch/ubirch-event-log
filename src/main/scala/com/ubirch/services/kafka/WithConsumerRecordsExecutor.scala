package com.ubirch.services.kafka

import org.apache.kafka.clients.consumer.ConsumerRecords

trait WithConsumerRecordsExecutor[K, V, R] {

  val executor: Executor[ConsumerRecords[K, V], R]

}