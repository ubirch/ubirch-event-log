package com.ubirch.util

object Exceptions {

  case class FilterEmptyException(message: String) extends Exception(message)

  case class EventLogParserException(message: String) extends Exception(message)

}
