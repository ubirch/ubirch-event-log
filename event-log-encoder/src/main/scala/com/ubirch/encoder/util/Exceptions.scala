package com.ubirch.encoder.util

import com.ubirch.encoder.services.kafka.consumer.EncoderPipeData
import com.ubirch.util.Exceptions.ExecutionException

/**
  * Namespace that contains the exceptions of the SDK.
  */

object Exceptions {

  //EXECUTION EXCEPTIONS

  case class JValueFromConsumerRecordException(message: String, pipeData: EncoderPipeData) extends ExecutionException(message)

  /**
    * Represents an exception thrown in the execution pipeline for when there has been an error creating
    * an EventLog from the Consumer Records that contains the Message Envelope
    * @param message Represents the error message.
    * @param pipeData Represents a convenience for handling and keeping data through the pipeline
    */
  case class EventLogFromConsumerRecordException(message: String, pipeData: EncoderPipeData) extends ExecutionException(message)

  /**
    * Represents an exception thrown in the execution pipeline for when there has been an error creating
    * a Producer Record object to be published into Kafka.
    * @param message Represents the error message.
    * @param pipeData Represents a convenience for handling and keeping data through the pipeline
    */
  case class CreateProducerRecordException(message: String, pipeData: EncoderPipeData) extends ExecutionException(message)

  /**
    * Represents an exception thrown when signing a EventLog message
    * @param message Represents the error message.
    * @param pipeData Represents the ProcessResult type from the Executor or the Executor Exception Handler
    */
  case class SigningEventLogException(message: String, pipeData: EncoderPipeData) extends ExecutionException(message)

  /**
    * Represents an exception thrown in the execution pipeline for when there has been an error committing the producer record value
    * into Kafka
    * @param message Represents the error message.
    * @param pipeData Represents a convenience for handling and keeping data through the pipeline
    */
  case class CommitException(message: String, pipeData: EncoderPipeData) extends ExecutionException(message)

  //EXECUTION EXCEPTIONS

}
