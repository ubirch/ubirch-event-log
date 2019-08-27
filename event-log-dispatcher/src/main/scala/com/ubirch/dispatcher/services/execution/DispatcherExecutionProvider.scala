package com.ubirch.dispatcher.services.execution

import java.util.concurrent.Executors

import com.typesafe.config.Config
import com.ubirch.ConfPaths.ExecutionContextConfPaths
import com.ubirch.services.execution.Execution
import javax.inject._

import scala.concurrent.{ ExecutionContext, ExecutionContextExecutor }

@Singleton
class DispatcherExecutionProvider @Inject() (config: Config) extends Provider[ExecutionContext] with Execution with ExecutionContextConfPaths {

  def threadPoolSize: Int = config.getInt(THREAD_POOL_SIZE)

  override implicit val ec: ExecutionContextExecutor = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(threadPoolSize))

  override def get(): ExecutionContext = ec

}
