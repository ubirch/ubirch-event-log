package com.ubirch.discovery.util

import com.ubirch.util.Exceptions.ExecutionException

/**
  * Namespace that contains the exceptions of the SDK.
  */

object Exceptions {

  //EXECUTION EXCEPTIONS

  case class ParsingError(message: String, reason: String) extends ExecutionException(message)

  case class SlaveTreeStrategyException(message: String, reason: String) extends ExecutionException(message)

  case class MasterTreeStrategyException(message: String, reason: String) extends ExecutionException(message)

  case class UPPStrategyException(message: String, reason: String) extends ExecutionException(message)

  //EXECUTION EXCEPTIONS

}
