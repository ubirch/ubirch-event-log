package com.ubirch.util

import com.ubirch.services.kafka.consumer.PipeData

/**
  * Namespace that contains the exceptions of the system and a abstract
  * exception to create more in other components that use the core component.
  */
object Exceptions {

  /**
    * Represents Generic Top Level Exception for the Event Log System
    * @param message Represents the error message.
    */
  abstract class EventLogException(message: String) extends Exception(message) {
    val name: String = this.getClass.getCanonicalName
  }

  //INJECTION EXCEPTIONS

  /**
    * Represents an Exception When Injecting a Dependency
    * @param message Represents the error message.
    */
  case class InjectionException(message: String) extends EventLogException(message)

  /**
    * Represents an Exception When the Injector is being created
    * @param message Represents the error message.
    */
  case class InjectorCreationException(message: String) extends EventLogException(message)

  //INJECTION EXCEPTIONS

  //CLUSTER EXCEPTIONS

  /**
    * Represents an Exception that is thrown when the cluster service has not contacts points
    * configured in the configuration file or in the env.
    * @param message Represents the error message.
    */
  case class NoContactPointsException(message: String) extends EventLogException(message)

  /**
    * Represents an Exception that is thrown when the connection service receives an empty keyspace
    * configured in the configuration file or set in the env.
    * @param message Represents the error message.
    */
  case class NoKeyspaceException(message: String) extends EventLogException(message)

  /**
    * Represents an Exception that is thrown when the Consistency Level is invalid.
    * @param message Represents the error message.
    */
  case class InvalidConsistencyLevel(message: String) extends EventLogException(message)

  /**
    * Represents an Exception that is thrown when the parsing of the contact points from a
    * string fail.
    * A correct string would look like: 127.0.0.1:9042, 127.0.0.2:9042
    * @param message Represents the error message.
    */
  case class InvalidContactPointsException(message: String) extends EventLogException(message)

  //CLUSTER EXCEPTIONS

  //EXECUTION EXCEPTIONS

  /**
    * Abstract class that represent a core Exception
    * @param message Represents the error message.
    */
  abstract class ExecutionException(message: String) extends EventLogException(message)

  /**
    * Represents an exception thrown when the value of the consumer record is empty.
    * @param message Represents the error message.
    */
  case class EmptyValueException(message: String, pipeData: PipeData) extends ExecutionException(message)

  /**
    * Represents an exception thrown when parsing the string value of the consumer record
    * to an EventLog.
    * @param message Represents the error message.
    * @param pipeData Represents the ProcessResult type from the Executor or the Executor Exception Handler
    */
  case class ParsingIntoEventLogException(message: String, pipeData: PipeData) extends ExecutionException(message)

  /**
    * Represents an exception thrown when signing a EventLog message
    * @param message Represents the error message.
    * @param pipeData Represents the ProcessResult type from the Executor or the Executor Exception Handler
    */
  case class SigningEventLogException(message: String, pipeData: PipeData) extends ExecutionException(message)

  /**
    * Exception thrown when processing an EventLog on the database.
    * @param message Represents the error message.
    * @param pipeData Represents the ProcessResult type from the Executor or the Executor Exception Handler
    * @param reason Represent the reason why it couldn't be proceeded.
    */
  case class EventLogDatabaseException(message: String, pipeData: PipeData, reason: String) extends ExecutionException(message)

  case class BasicCommitException(message: String) extends ExecutionException(message)

  //EXECUTION EXCEPTIONS

}
