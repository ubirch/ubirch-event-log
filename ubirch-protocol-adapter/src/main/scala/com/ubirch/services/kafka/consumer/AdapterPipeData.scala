package com.ubirch.services.kafka.consumer

import java.util.UUID

import com.ubirch.kafka.consumer.ProcessResult
import com.ubirch.models.EventLog
import com.ubirch.util.UUIDHelper
import org.apache.kafka.clients.consumer.ConsumerRecord

/**
  * Represents the ProcessResult implementation for a the string consumer.
  *
  * @param consumerRecord Represents the data received in the poll from Kafka
  * @param eventLog Represents the event log type. It is here for informative purposes.
  */
case class AdapterPipeData(consumerRecord: ConsumerRecord[String, String], eventLog: Option[EventLog]) extends ProcessResult[String, String] {
  override val id: UUID = UUIDHelper.randomUUID
}
