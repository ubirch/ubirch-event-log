package com.ubirch.encoder.services.execution

import java.util.concurrent.Executors
import com.typesafe.config.Config
import com.ubirch.ConfPaths.ExecutionContextConfPaths
import com.ubirch.services.execution.Execution
import monix.execution.Scheduler

import javax.inject._
import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor }

/**
  * Represents a provider for an execution context
  * @param config Represents a config object
  */
@Singleton
class EncodingExecutionProvider @Inject() (config: Config) extends Provider[ExecutionContext] with Execution with ExecutionContextConfPaths {

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
class EncodingSchedulerProvider @Inject() (ec: ExecutionContext) extends Provider[Scheduler] with SchedulerBase {

  override implicit val scheduler: Scheduler = monix.execution.Scheduler(ec)

  override def get(): Scheduler = scheduler

}
