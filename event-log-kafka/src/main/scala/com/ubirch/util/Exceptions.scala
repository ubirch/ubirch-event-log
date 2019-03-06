package com.ubirch.util

import org.apache.kafka.clients.consumer.CommitFailedException
import org.apache.kafka.common.errors.TimeoutException

/**
  * Namespace that contains the exceptions of the system and a abstract
  * exception to create more in other components that use the core component.
  */
object Exceptions {

  /**
    * Abstract class that represent a core Exception
    * @param message Represents the error message.
    */
  abstract class ConsumptionException(message: String) extends Exception(message) {
    val name: String = this.getClass.getCanonicalName
  }

  case class CommitTimeoutException(message: String, commitFunc: () => Unit, timeoutException: TimeoutException) extends ConsumptionException(message)

  case class MaxNumberOfCommitAttemptsException(message: String, reason: String, timeoutException: Either[CommitTimeoutException, CommitFailedException]) extends ConsumptionException(message)

  case class ConsumerCreationException(message: String, reason: String) extends ConsumptionException(message)

  case class EmptyTopicException(message: String) extends ConsumptionException(message)

  case class NeedForShutDownException(message: String, reason: String) extends ConsumptionException(message)

  case class NeedForPauseException(message: String, reason: String) extends ConsumptionException(message)

  case class NeedForResumeException(message: String) extends ConsumptionException(message)

}
