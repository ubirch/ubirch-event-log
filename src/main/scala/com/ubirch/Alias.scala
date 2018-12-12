package com.ubirch

import com.ubirch.services.kafka.Executor
import org.apache.kafka.clients.consumer.ConsumerRecords

object Alias {

  type ExecutorProcess[R] = Executor[ConsumerRecords[String, String], R]

}
