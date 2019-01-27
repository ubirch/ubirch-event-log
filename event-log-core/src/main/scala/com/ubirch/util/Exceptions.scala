package com.ubirch.util

import com.ubirch.models.EventLog

/**
  * Namespace that contains the exceptions of the system and a abstract
  * exception to create more in other components that use the core component.
  */
object Exceptions {

  /**
    * Abstract class that represent a core Exception
    * @param message Represents the error message.
    */
  abstract class ExecutionException(message: String) extends Exception(message) {
    val name = this.getClass.getCanonicalName
  }

  /**
    * Represents an exception thrown when the value of the consumer record is empty.
    * @param message Represents the error message.
    */
  case class EmptyValueException(message: String) extends ExecutionException(message)

  /**
    * Represents an exception thrown when parsing the string value of the consumer record
    * to an EventLog.
    * @param message Represents the error message.
    * @param value Represent the input value that caused the error.
    */
  case class ParsingIntoEventLogException(message: String, value: String) extends ExecutionException(message)

  /**
    * Exception thrown when storing an EventLog to the database.
    * @param message Represents the error message.
    * @param eventLog Represent the EventLog message.
    * @param reason Represent the reason why it couldn't be stored.
    */
  case class StoringIntoEventLogException(message: String, eventLog: EventLog, reason: String) extends ExecutionException(message)

}
