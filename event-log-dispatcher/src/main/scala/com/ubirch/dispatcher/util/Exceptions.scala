package com.ubirch.dispatcher.util

import com.ubirch.dispatcher.services.kafka.consumer.DispatcherPipeData
import com.ubirch.util.Exceptions.ExecutionException

/**
  * Namespace that contains the exceptions of the SDK.
  */

object Exceptions {

  //EXECUTION EXCEPTIONS

  case class EmptyValueException(message: String, pipeData: DispatcherPipeData) extends ExecutionException(message)

  case class ParsingIntoEventLogException(message: String, pipeData: DispatcherPipeData) extends ExecutionException(message)

  case class CreateProducerRecordException(message: String, pipeData: DispatcherPipeData) extends ExecutionException(message)

  case class CommitException(message: String, pipeData: DispatcherPipeData) extends ExecutionException(message)

  case class BasicCommitException(message: String) extends ExecutionException(message)

  //EXECUTION EXCEPTIONS

}