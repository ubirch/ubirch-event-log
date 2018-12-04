package com.ubirch

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.Events
import com.ubirch.services.execution.Execution
import com.ubirch.util.Boot

object App extends Boot with LazyLogging with Execution {

  val g = get[Events]

  def main(args: Array[String]): Unit = {
    println("Im here")

  }

}
