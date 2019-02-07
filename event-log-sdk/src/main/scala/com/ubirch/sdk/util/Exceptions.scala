package com.ubirch.sdk.util

import com.ubirch.models.EventLog
import com.ubirch.util.Exceptions.EventLogException

/**
  * Namespace that contains the exceptions of the SDK.
  */

object Exceptions {

  //EXECUTION EXCEPTIONS

  /**
    * Represents an exception thrown when
    * @param v Represents the value T that is tried be converted into an Event.
    * @param message Represents the error message.
    * @tparam T Represents the type T an Event will be created from.
    */

  case class CreateEventFromException[T](v: T, message: String) extends EventLogException(message)

  /**
    * Represents an exception that is thrown when trying to commit and EventLog
    * with a String Producer.
    * @param eventLog Represent the outer message that is sent to Kafka.
    * @param message Represents the error message.
    */
  case class CreateProducerRecordException(eventLog: EventLog, message: String) extends EventLogException(message)

  /**
    * Represents an exception that is thrown when trying to commit an
    * ProducerRecord created from an EventLog
    * with a String Producer.
    * @param eventLog Represent the outer message that is sent to Kafka.
    * @param message Represents the error message.
    */
  case class CommitException(eventLog: EventLog, message: String) extends EventLogException(message)

  /**
    * Represents an exception that is thrown when trying to synchronously handle
    * a RecordMetadata gotten from the Commit phase
    * with a String Producer.
    * @param eventLog Represent the outer message that is sent to Kafka.
    * @param message Represents the error message.
    */
  case class CommitHandlerSyncException(eventLog: EventLog, message: String) extends EventLogException(message)

  /**
    * Represents an exception that is thrown when trying to asynchronously handle
    * a RecordMetadata gotten from the Commit phase
    * @param eventLog Represent the outer message that is sent to Kafka.
    * @param message Represents the error message.
    */
  case class CommitHandlerASyncException(eventLog: EventLog, message: String) extends EventLogException(message)

  /**
    * Represents an exception that is thrown when trying to stealth-asynchronously handle
    * a RecordMetadata gotten from the Commit phase
    * This mode is "fire and forget" type of response management.
    * @param eventLog Represent the outer message that is sent to Kafka.
    * @param message Represents the error message.
    */
  case class CommitHandlerStealthAsyncException(eventLog: EventLog, message: String) extends EventLogException(message)

  //EXECUTION EXCEPTIONS

}
