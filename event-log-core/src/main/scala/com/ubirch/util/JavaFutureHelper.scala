package com.ubirch.util

import java.util.concurrent.{ Future => JavaFuture }

import com.ubirch.services.execution.Execution

import scala.concurrent.{ Future, blocking }

object JavaFutureHelper extends Execution {

  def toScalaFuture[T](javaFuture: JavaFuture[T]) =
    Future {
      blocking {
        javaFuture.get()
      }
    }

}
