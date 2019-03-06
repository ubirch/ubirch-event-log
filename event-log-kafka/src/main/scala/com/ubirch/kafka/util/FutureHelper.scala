package com.ubirch.kafka.util

import java.util.concurrent.{ CountDownLatch, Future => JavaFuture }

import monix.execution.Scheduler.{ global => scheduler }

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ Future, blocking }

/**
  * A helper object for future-related stuff
  */
object FutureHelper extends Execution {

  def withBlock[T](f: () => T): Future[T] = {
    Future {
      blocking {
        f()
      }
    }
  }

  def fromJavaFuture[T](javaFuture: JavaFuture[T]): Future[T] = {
    withBlock(
      () => javaFuture.get()
    )
  }

  def delay[T](duration: FiniteDuration)(t: => T): T = {
    val countDown = new CountDownLatch(1)
    scheduler.scheduleOnce(duration)(countDown.countDown())
    countDown.await()
    t
  }

}
