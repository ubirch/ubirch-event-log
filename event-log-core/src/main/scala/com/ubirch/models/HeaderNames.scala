package com.ubirch.models

trait HeaderNames {

  val ORIGIN = "origin"
  val TRACE = "trace"
  val BLUE_MARK = "blue_mark"
  val REQUEST_ID = "request_id"
  val DISPATCHER = "dispatcher"

}

object HeaderNames extends HeaderNames
