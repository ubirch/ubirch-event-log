package com.ubirch.models

trait HeaderNames {

  final val ORIGIN = "origin"
  final val TRACE = "trace"
  final val BLUE_MARK = "blue_mark"
  final val REQUEST_ID = "request_id"
  final val DISPATCHER = "dispatcher"
  final val DEVICE_ID = "device_id"

}

object HeaderNames extends HeaderNames
