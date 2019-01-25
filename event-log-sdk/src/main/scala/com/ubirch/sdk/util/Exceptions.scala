package com.ubirch.sdk.util

import com.ubirch.models.EventLog
import com.ubirch.util.Exceptions.ExecutionException

object Exceptions {

  case class CreateEventFromException[T](v: T, message: String) extends ExecutionException(message)

  case class CommitException(eventLog: EventLog, message: String) extends ExecutionException(message)

}
