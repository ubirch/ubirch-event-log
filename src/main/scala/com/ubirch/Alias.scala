package com.ubirch

import com.ubirch.services.kafka.{ Executor, MessageEnvelope }
import org.apache.kafka.clients.consumer.ConsumerRecords

object Alias {

  type MessagesInEnvelope[R] = Vector[MessageEnvelope[R]]
  type ExecutorProcessRaw[R] = Executor[ConsumerRecords[String, String], R]
  type ExecutorProcessEnveloped[R] = Executor[Vector[MessageEnvelope[String]], R]

}
