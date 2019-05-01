package com.ubirch.services.kafka.consumer

import java.util.UUID

import com.ubirch.kafka.consumer.ProcessResult
import com.ubirch.models.EventLog
import com.ubirch.util.{ Decision, UUIDHelper }
import org.apache.kafka.clients.producer.{ ProducerRecord, RecordMetadata }

/**
  * Represents a pipeline object for the EventLog
  * @tparam V Represents the type of the Value for the consumer.
  */
trait EventLogPipeData[V] extends ProcessResult[String, V] {
  val id: UUID = UUIDHelper.randomUUID
  val eventLog: Option[EventLog]
}

trait EventLogsPipeData[V] extends ProcessResult[String, V] {
  val id: UUID = UUIDHelper.randomUUID
  val eventLogs: Vector[EventLog]
}

trait WithPublishingData[V] {
  val producerRecord: Option[Decision[ProducerRecord[String, V]]]
  val recordMetadata: Option[RecordMetadata]
}

