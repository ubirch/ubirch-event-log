package com.ubirch.util

object Exceptions {

  case class ExecutionException(message: String, `type`: ExecutionException.Value) extends Exception(message)

  object ExecutionException extends Enumeration {
    val EmptyValue = Value
    val ParsingIntoEventLog = Value
  }

}
