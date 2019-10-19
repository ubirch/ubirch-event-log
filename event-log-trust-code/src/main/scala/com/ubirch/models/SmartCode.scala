package com.ubirch.models

import com.ubirch.sdk.EventLogging
import org.json4s.JValue

abstract class SmartCodeBase extends EventLogging {
  def init(id: String): Unit
  def put(id: String, state: JValue): Unit
  def get(id: String, state: JValue): Unit
}


abstract class SmartCode extends SmartCodeBase {
  override def put(id: String, state: JValue): Unit = log(state).withCustomerId(id).commitAsync
  override def get(id: String, state: JValue): Unit = ???
}
