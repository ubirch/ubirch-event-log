package com.ubirch.kafka.util

import com.ubirch.util.FutureHelper

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.language.implicitConversions

/**
  * It is an enriched iterator
  * @param iterator Represents the iterator that gets enriched
  * @tparam A Represents the type of the elems found in the iterator
  */
case class EnrichedIterator[A](iterator: Iterator[A])(implicit ec: ExecutionContext) {

  lazy val futureHelper = new FutureHelper()

  def delayOnNext(duration: FiniteDuration): Iterator[A] = iterator.map { x =>
    futureHelper.delay(duration)(x)
  }

  def consumeWithFinalDelay[U](f: A => U)(duration: FiniteDuration): Unit = {
    while (iterator.hasNext) f(iterator.next())
    futureHelper.delay(duration)(())
  }

}

/**
  * Util that contains the implicits to create enriched values.
  */
object Implicits {

  implicit def enrichedIterator[T](iterator: Iterator[T])(implicit ec: ExecutionContext): EnrichedIterator[T] = EnrichedIterator[T](iterator)

}
