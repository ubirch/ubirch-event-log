package com.ubirch.services.execution

import java.util.concurrent.Executors

import javax.inject.Provider

import scala.concurrent.ExecutionContext

/**
  * Represents the Execution Context Component used in the system
  */
trait Execution {
  implicit def ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(5))

}

/**
  * Represents the Execution Context provider.
  * Whenever someone injects an ExecutionContext, this provider defines what will
  * be returned.
  */
class ExecutionProvider extends Provider[ExecutionContext] with Execution {
  override def get(): ExecutionContext = ec
}

