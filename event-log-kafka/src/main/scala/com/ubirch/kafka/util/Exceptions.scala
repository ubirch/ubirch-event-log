package com.ubirch.kafka.util

import org.apache.kafka.clients.consumer.CommitFailedException
import org.apache.kafka.common.errors.TimeoutException

/**
  * Namespace that contains the exceptions of the system and an abstract
  * exception to create more in other components that use the core component.
  */
object Exceptions {

  /**
    * Abstract class that represent a core Consumption Exception
    * @param message Represents the error message.
    */
  abstract class ConsumptionException(message: String) extends Exception(message) {
    val name: String = this.getClass.getCanonicalName
  }

  /**
    * Represents a wrapped timeout exception. It is used to control the attempts when commiting.
    * @param message Represents the error message.
    * @param commitFunc Represents a commit function that will be applied when attempting again.
    * @param timeoutException Represents the kafka time out exception.
    */
  case class CommitTimeoutException(message: String, commitFunc: () => Unit, timeoutException: TimeoutException) extends ConsumptionException(message)

  /**
    * Represents a exception that is throw the max. number of attempts configured is reached.
    * When this happens, the signal is sent to a higher level floor to control.
    * @param message Represents the error message.
    * @param reason Represents the reason why this exception has been thrown.
    * @param timeoutException Represents the kafka time out exception.
    */
  case class MaxNumberOfCommitAttemptsException(message: String, reason: String, timeoutException: Either[CommitTimeoutException, CommitFailedException]) extends ConsumptionException(message)

  /**
    * Represents a signal that is sent when there has been an error creating the consumer.
    * @param message Represents the error message.
    * @param reason Represents the reason why this exception has been thrown.
    */
  case class ConsumerCreationException(message: String, reason: String) extends ConsumptionException(message)

  /**
    * Represents a signal that is sent when no topics has been set up.
    * @param message Represents the error message.
    */
  case class EmptyTopicException(message: String) extends ConsumptionException(message)

  /**
    * Represents an exception for when a consumer records controller has not been set up.
    * @param message Represents the error message.
    */
  case class ConsumerRecordsControllerException(message: String) extends ConsumptionException(message)

  /**
    * Represents a signal to shutdown the consumer runner.
    * @param message Represents the error message.
    * @param reason Represents the reason why this exception has been thrown.
    */
  case class NeedForShutDownException(message: String, reason: String) extends ConsumptionException(message)

  /**
    * Represents a signal that can be used to tell the consumer to pause.
    * This signal should be sent when processing. The system will read it and do the
    * corresponding pause
    * @param message Represents the error message.
    * @param reason Represents the reason why this exception has been thrown.
    */

  case class NeedForPauseException(message: String, reason: String) extends ConsumptionException(message)

  /**
    * Represents a signal that is used to ask the consumer to resume polling.
    * This signal is usually scheduled when receiving a NeedForPauseException
    * @param message Represents the error message.
    */
  case class NeedForResumeException(message: String) extends ConsumptionException(message)

  /**
    * Represents an exception for when there's been an error creating the producer
    * @param message Represents the error message.
    * @param reason Represents the reason why this exception has been thrown.
    */
  case class ProducerCreationException(message: String, reason: String) extends ConsumptionException(message)

  /**
    * Represents an exception for when the producer hasn't been started and yet it's being tried to be used.
    * @param message Represents the error message.
    */
  case class ProducerNotStartedException(message: String) extends ConsumptionException(message)

}
