package com.ubirch.verification.util

import com.ubirch.util.Exceptions.ExecutionException
import com.ubirch.verification.services.eventlog.LookupPipeDataNew

/**
  * Namespace that contains the exceptions of the SDK.
  */

object Exceptions {

  //EXECUTION EXCEPTIONS

  case class LookupExecutorException(message: String, pipeData: LookupPipeDataNew, reason: String) extends ExecutionException(message)

  case class CreateProducerRecordException(message: String, pipeData: LookupPipeDataNew) extends ExecutionException(message)

  case class SigningEventLogException(message: String, pipeData: LookupPipeDataNew) extends ExecutionException(message)

  case class CommitException(message: String, pipeData: LookupPipeDataNew) extends ExecutionException(message)

  //EXECUTION EXCEPTIONS

}
