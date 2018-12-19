package com.ubirch.util

import com.ubirch.models.EventLog

object Exceptions {

  abstract class ExecutionException(message: String) extends Exception(message) {
    val name = this.getClass.getCanonicalName
  }

  case class EmptyValueException(message: String) extends ExecutionException(message)

  case class ParsingIntoEventLogException(message: String, value: String) extends ExecutionException(message)

  case class StoringIntoEventLogException(message: String, eventLog: EventLog, reason: String) extends ExecutionException(message)

}
