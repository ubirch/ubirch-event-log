package com.ubirch.util

import java.util.concurrent.{ CountDownLatch, TimeUnit, Future => JavaFuture }

import monix.execution.Scheduler.{ global => scheduler }

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ ExecutionContext, Future, blocking }

/**
  * A helper class for future-related stuff
  */
class FutureHelper()(implicit ec: ExecutionContext) {

  def withBlock[T](f: () => T): Future[T] = {
    Future {
      blocking {
        f()
      }
    }
  }

  def fromJavaFuture[T](javaFuture: JavaFuture[T]): Future[T] = {
    withBlock(
      () => javaFuture.get(3000, TimeUnit.MILLISECONDS)
    )
  }

  def delay[T](duration: FiniteDuration)(t: => T): T = {
    val countDown = new CountDownLatch(1)
    scheduler.scheduleOnce(duration)(countDown.countDown())
    countDown.await()
    t
  }

}
