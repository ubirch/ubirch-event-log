package com.ubirch.kafka.util

import scala.concurrent.ExecutionContext

/**
  * Represents the Execution Context Component used in the system
  */
trait Execution {
  implicit def ec: ExecutionContext = scala.concurrent.ExecutionContext.global
}
