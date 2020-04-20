package com.ubirch.lookup.util

import com.typesafe.scalalogging.LazyLogging

import scala.util.Try

object Timer extends LazyLogging {

  case class Timed[R](result: Try[R], timeTaken: Interval, description: String) {
    lazy val elapsed: Long = timeTaken.time
    def logTimeTaken(arg: String = description): Unit = {
      if (elapsed > 1000) {
        logger.warn(s"Time to do $arg took $elapsed ms!")
      } else {
        logger.debug("Took " + elapsed + " ms to " + arg)
      }
    }
    def getResult: R = result.get
  }

  def time[R](block: => R, description: String = ""): Timed[R] = {
    val t0 = System.currentTimeMillis()
    val result = Try(block)
    val t1 = System.currentTimeMillis()
    Timed(result, Interval(t0, t1), description)
  }

  case class Interval(start: Long, end: Long) {
    def time: Long = end - start
  }

}
