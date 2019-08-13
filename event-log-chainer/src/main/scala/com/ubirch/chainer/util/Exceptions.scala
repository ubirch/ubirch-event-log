package com.ubirch.chainer.util

import com.ubirch.chainer.services.kafka.consumer.ChainerPipeData
import com.ubirch.util.Exceptions.ExecutionException

case class WrongParamsException(message: String, pipeData: ChainerPipeData) extends ExecutionException(message)

/**
  * Represents an exception thrown when the value of the consumer record is empty.
  *
  * @param message Represents the error message.
  */
case class EmptyValueException(message: String, pipeData: ChainerPipeData) extends ExecutionException(message)

/**
  * Represents an exception thrown when parsing the string value of the consumer record
  * to an EventLog.
  * @param message Represents the error message.
  * @param pipeData Represents the ProcessResult type from the Executor or the Executor Exception Handler
  */
case class ParsingIntoEventLogException(message: String, pipeData: ChainerPipeData) extends ExecutionException(message)

/**
  * Represents an exception thrown when signing a EventLog message
  * @param message Represents the error message.
  * @param pipeData Represents the ProcessResult type from the Executor or the Executor Exception Handler
  */
case class SigningEventLogException(message: String, pipeData: ChainerPipeData) extends ExecutionException(message)

case class TreeEventLogCreationException(message: String, pipeData: ChainerPipeData) extends ExecutionException(message)

/**
  * Represents an exception thrown in the execution pipeline for when there has been an error creating
  * a Producer Record object to be published into Kafka.
  * @param message Represents the error message.
  * @param pipeData Represents a convenience for handling and keeping data through the pipeline
  */
case class CreateTreeProducerRecordException(message: String, pipeData: ChainerPipeData) extends ExecutionException(message)

/**
  * Represents an exception thrown in the execution pipeline for when there has been an error committing the producer record value
  * into Kafka
  * @param message Represents the error message.
  * @param pipeData Represents a convenience for handling and keeping data through the pipeline
  */
case class CommitException(message: String, pipeData: ChainerPipeData) extends ExecutionException(message)
