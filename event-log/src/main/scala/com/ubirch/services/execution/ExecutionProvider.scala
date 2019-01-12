package com.ubirch.services.execution

import javax.inject.Provider

import scala.concurrent.ExecutionContext

trait Execution {
  implicit def ec = scala.concurrent.ExecutionContext.global
}

class ExecutionProvider extends Provider[ExecutionContext] with Execution {
  override def get(): ExecutionContext = ec
}

