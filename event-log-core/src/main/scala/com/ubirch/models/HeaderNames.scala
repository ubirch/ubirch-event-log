package com.ubirch.models

trait HeaderNames {

  val ORIGIN = "origin"
  val TRACE = "trace"
  val BLUE_MARK = "blue-mark"
  val REQUEST_ID = "request-id"
  val DISPATCHER = "dispatcher"

}

object HeaderNames extends HeaderNames
