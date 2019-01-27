package com.ubirch.services.execution

import javax.inject.Provider

import scala.concurrent.ExecutionContext

/**
  * Represents the Execution Context Component used in the system
  */
trait Execution {
  implicit def ec = scala.concurrent.ExecutionContext.global
}

/**
  * Represents the Execution Context provider.
  * Whenever someone injects an ExecutionContext, this provider defines what will
  * be returned.
  */
class ExecutionProvider extends Provider[ExecutionContext] with Execution {
  override def get(): ExecutionContext = ec
}

