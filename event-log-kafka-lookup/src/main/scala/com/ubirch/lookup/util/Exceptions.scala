package com.ubirch.lookup.util

import com.ubirch.lookup.services.kafka.consumer.LookupPipeData
import com.ubirch.util.Exceptions.ExecutionException

/**
  * Namespace that contains the exceptions of the SDK.
  */

object Exceptions {

  //EXECUTION EXCEPTIONS

  case class LookupExecutorException(message: String, pipeData: LookupPipeData) extends ExecutionException(message)

  case class CreateProducerRecordException(message: String, pipeData: LookupPipeData) extends ExecutionException(message)

  case class SigningEventLogException(message: String, pipeData: LookupPipeData) extends ExecutionException(message)

  case class CommitException(message: String, pipeData: LookupPipeData) extends ExecutionException(message)

  //EXECUTION EXCEPTIONS

}
