package com.ubirch.models

import org.json4s.JValue

import java.util.Date

/**
  * Concrete type for the EventLogBase whose type T is JValue
  *
  * @param id            String that identifies the EventLog. It can be a hash or a UUID or anything unique
  * @param customerId    Represents an id for a customer id.
  * @param serviceClass  Represents the name from where the log comes.
  *                      E.G: The name of the class.
  * @param category      Represents the category for the event. This is useful for
  *                      adding layers of description to the event.
  * @param event         Represents the event that is to be recorded.
  * @param eventTime     Represents the time when the event log was created.
  * @param eventTimeInfo Represents the time of the event in an unfolded manner.
  *                      This is useful and needed for making cluster keys with
  *                      the time of the event possible. Helpers are provided
  *                      to support its creation from the eventTime.
  * @param signature     Represents the signature for the event log.
  * @param nonce         Represents a value that can be used to calculate the hash of the event.
  */

case class EventLogRow(
                        id: String,
                        customerId: String,
                        serviceClass: String,
                        category: String,
                        event: JValue,
                        eventTime: Date,
                        eventTimeInfo: TimeInfo,
                        signature: String,
                        nonce: String,
                        status: Option[String] = None
                      )

object EventLogRow {
  def fromEventLog(eventLog: EventLog): EventLogRow = {
    EventLogRow(
      id = eventLog.id,
      customerId = eventLog.customerId,
      serviceClass = eventLog.serviceClass,
      category = eventLog.category,
      event = eventLog.event,
      eventTime = eventLog.eventTime,
      eventTimeInfo = TimeInfo.fromDate(eventLog.eventTime),
      signature = eventLog.signature,
      nonce = eventLog.nonce
    )
  }

  def toEventLog(eventLogRow: EventLogRow): EventLog = {
    EventLog(eventLogRow.event)
      .withCategory(eventLogRow.category)
      .withEventTime(eventLogRow.eventTime)
      .withSignature(eventLogRow.signature)
      .withNonce(eventLogRow.nonce)
      .withServiceClass(eventLogRow.serviceClass)
      .withNewId(eventLogRow.id)
      .withCustomerId(eventLogRow.customerId)

  }
}
