package com.ubirch.util

import java.util.concurrent.{ CountDownLatch, TimeUnit, Future => JavaFuture }

import scala.concurrent.duration.{ Duration, FiniteDuration }
import scala.concurrent.{ Await, ExecutionContext, Future, blocking }

/**
  * A helper class for future-related stuff
  */
class FutureHelper()(implicit ec: ExecutionContext) {

  implicit val scheduler = monix.execution.Scheduler(ec)

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

object FutureHelper {

  def await[T](future: Future[T]): T = await(future, Duration.Inf)

  def await[T](future: Future[T], atMost: Duration): T = Await.result(future, atMost)

}
