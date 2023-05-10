package com.ubirch.services.execution

import java.util.concurrent.Executors
import com.typesafe.config.Config
import com.ubirch.ConfPaths.ExecutionContextConfPaths
import monix.execution.Scheduler

import javax.inject._
import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor }

/**
  * Represents the Execution Context Component used in the system
  */
trait Execution {
  implicit def ec: ExecutionContextExecutor
}

trait ExecutionImpl extends Execution {

  override implicit def ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(5))
}

/**
  * Represents the Execution Context provider.
  * Whenever someone injects an ExecutionContext, this provider defines what will
  * be returned.
  */
@Singleton
class ExecutionProvider @Inject() (config: Config) extends Provider[ExecutionContext] with Execution with ExecutionContextConfPaths {

  def threadPoolSize: Int = config.getInt(THREAD_POOL_SIZE)

  override implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(threadPoolSize))

  override def get(): ExecutionContext = ec

}

/**
  * Represents a Scheduler used in the system
  */
trait SchedulerBase {
  implicit val scheduler: Scheduler
}

/**
  * Represents the Scheduler Provider
  * @param ec Represents the execution context for async processes.
  */
@Singleton
class SchedulerProvider @Inject() (ec: ExecutionContext) extends Provider[Scheduler] with SchedulerBase {

  override implicit val scheduler: Scheduler = monix.execution.Scheduler(ec)

  override def get(): Scheduler = scheduler

}
