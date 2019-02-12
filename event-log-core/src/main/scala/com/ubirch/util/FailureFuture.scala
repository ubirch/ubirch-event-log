package com.ubirch.util

import scala.concurrent.Promise
import scala.util.{ Failure, Success }

trait FailureFuture {

  type DummyType = Unit

  private val failure = Promise[DummyType]()

  def somethingWentWrong(exception: Throwable): Promise[DummyType] = failure.failure(exception)

  def checkIfSomethingWentWrong(): DummyType = {
    if (failure.isCompleted) {
      failure.future.value match {
        case Some(value) =>
          value match {
            case Success(_) =>
            case Failure(exception) => throw exception
          }
        case None =>
      }
    }
  }

}
