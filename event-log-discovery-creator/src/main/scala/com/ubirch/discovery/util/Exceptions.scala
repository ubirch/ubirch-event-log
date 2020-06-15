package com.ubirch.discovery.util

import com.ubirch.models.EventLog
import com.ubirch.util.Exceptions.ExecutionException

/**
  * Namespace that contains the exceptions of the SDK.
  */

object Exceptions {

  //EXECUTION EXCEPTIONS

  case class ParsingError(message: String, reason: String, value: String) extends ExecutionException(message)

  abstract class StrategyException(val message: String, val reason: String, val eventLog: EventLog) extends ExecutionException(message)

  case class SlaveTreeStrategyException(override val message: String, override val reason: String, override val eventLog: EventLog) extends StrategyException(message, reason, eventLog)

  case class MasterTreeStrategyException(override val message: String, override val reason: String, override val eventLog: EventLog) extends StrategyException(message, reason, eventLog)

  case class UPPStrategyException(override val message: String, override val reason: String, override val eventLog: EventLog) extends StrategyException(message, reason, eventLog)

  case class PublicKeyStrategyException(override val message: String, override val reason: String, override val eventLog: EventLog) extends StrategyException(message, reason, eventLog)

  case class UnknownStrategyException(override val message: String, override val reason: String, override val eventLog: EventLog) extends StrategyException(message, reason, eventLog)

  //EXECUTION EXCEPTIONS

}
