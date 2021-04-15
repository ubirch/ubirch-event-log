package com.ubirch.verification.util

import com.ubirch.util.Exceptions.ExecutionException
import com.ubirch.verification.models.{ AcctEvent, LookupResult }

/**
  * Namespace that contains the exceptions of the SDK.
  */

object Exceptions {

  case class LookupExecutorException(message: String, result: Option[LookupResult], reason: String) extends ExecutionException(message)

  case class InvalidOtherClaims(message: String, value: String) extends ExecutionException(message)
  case class InvalidAllClaims(message: String, value: String) extends ExecutionException(message)
  case class InvalidSpecificClaim(message: String, value: String) extends ExecutionException(message)

  case class FailedKafkaPublish(acctEvent: AcctEvent, maybeThrowable: Option[Throwable])
    extends ExecutionException(maybeThrowable.map(_.getMessage).getOrElse("Failed Publish"))

  case class InvalidUUID(message: String, value: String) extends ExecutionException(value)
  case class InvalidOrigin(message: String, value: String) extends ExecutionException(value)

}
