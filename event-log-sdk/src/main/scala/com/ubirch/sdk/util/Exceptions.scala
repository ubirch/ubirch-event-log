package com.ubirch.sdk.util

import com.ubirch.models.EventLog
import com.ubirch.util.Exceptions.ExecutionException

/**
  * Namespace that contains the exceptions of the SDK.
  */

object Exceptions {

  /**
    * Represents an exception thrown when
    * @param v Represents the value T that is tried be converted into an Event.
    * @param message Represents the error message.
    * @tparam T Represents the type T an Event will be created from.
    */

  case class CreateEventFromException[T](v: T, message: String) extends ExecutionException(message)

  /**
    * Represents an exception that is thrown when trying to commit and EventLog
    * with a String Producer.
    * @param eventLog Represent the outer message that is sent to Kafka.
    * @param message Represents the error message.
    */
  case class CommitException(eventLog: EventLog, message: String) extends ExecutionException(message)

}
