package com.ubirch.verification.util

import com.ubirch.util.Exceptions.ExecutionException
import com.ubirch.verification.models.LookupResult

/**
  * Namespace that contains the exceptions of the SDK.
  */

object Exceptions {

  case class LookupExecutorException(message: String, result: Option[LookupResult], reason: String) extends ExecutionException(message)

}
