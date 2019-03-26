package com.ubirch.adapter.util

import com.ubirch.adapter.services.kafka.consumer.MessageEnvelopePipeData
import com.ubirch.util.Exceptions.ExecutionException

/**
  * Namespace that contains the exceptions of the SDK.
  */

object Exceptions {

  //EXECUTION EXCEPTIONS

  case class EventLogFromConsumerRecordException(message: String, pipeData: MessageEnvelopePipeData) extends ExecutionException(message)

  case class CreateProducerRecordException(message: String, pipeData: MessageEnvelopePipeData) extends ExecutionException(message)

  case class CommitException(message: String, pipeData: MessageEnvelopePipeData) extends ExecutionException(message)

  //EXECUTION EXCEPTIONS

}
