package com.ubirch.sdk

import com.ubirch.sdk.process.EventLogger
import org.json4s.JValue

trait EventLogging {

  def log(message: JValue): EventLogger = EventLogger(getClass.getName, "", message)

  def log(message: JValue, category: String): EventLogger = EventLogger(getClass.getName, category, message)

  def log[T: Manifest](message: T, category: String): EventLogger = EventLogger(getClass.getName, category, message)

  def log[T: Manifest](message: T): EventLogger = EventLogger(getClass.getName, "", message)

}
