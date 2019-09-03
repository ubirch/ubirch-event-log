package com.ubirch.process

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.services.kafka.consumer.PipeData
import com.ubirch.services.metrics.{ Counter, DefaultMetricsLoggerCounter }
import javax.inject._

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

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

@Singleton
class MetricsLoggerBasic @Inject() (@Named(DefaultMetricsLoggerCounter.name) counter: Counter) extends LazyLogging {

  def incSuccess: Unit = counter.counter.labels("success").inc()

  def incFailure: Unit = counter.counter.labels("failure").inc()

}

@Singleton
class MetricsLogger @Inject() (logger: MetricsLoggerBasic)(implicit ec: ExecutionContext)
  extends Executor[Future[PipeData], Future[PipeData]] {

  override def apply(v1: Future[PipeData]): Future[PipeData] = {
    v1.onComplete {
      case Success(_) =>
        logger.incSuccess
      case Failure(_) =>
        logger.incFailure
    }

    v1
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
