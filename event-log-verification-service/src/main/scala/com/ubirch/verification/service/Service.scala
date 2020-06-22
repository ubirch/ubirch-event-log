package com.ubirch.verification.service

import com.ubirch.util.Boot

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

object Service extends Boot(LookupServiceBinder.modules) {

  def main(args: Array[String]): Unit = {
    implicit val globalExec: ExecutionContextExecutor = ExecutionContext.global

  }

}
