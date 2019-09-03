package com.ubirch.process

import javax.inject._

/**
  * Represents a process to be executed.
  * It allows for Executor composition with the operator andThen
  * @tparam T1 the input to the pipe
  * @tparam R  the output of the pipe
  */
trait Executor[-T1, +R] extends (T1 => R) {
  self =>

  override def apply(v1: T1): R

  def andThen[Q](other: Executor[R, Q]): Executor[T1, Q] = {
    v1: T1 => other(self(v1))
  }

}

/**
  * A convenience type to aggregate executors for later injection
  */

trait ExecutorFamily {

  def loggerExecutor: LoggerExecutor

}

@Singleton
case class DefaultExecutorFamily @Inject() (loggerExecutor: LoggerExecutor) extends ExecutorFamily
