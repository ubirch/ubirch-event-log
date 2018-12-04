package com

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.Duration

package object ubirch {

  val r = scala.util.Random

  def await[T](f: Future[T]): T = Await.result(f, Duration.Inf)

}
