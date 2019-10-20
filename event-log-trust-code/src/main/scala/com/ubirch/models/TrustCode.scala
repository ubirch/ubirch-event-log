package com.ubirch.models

import com.ubirch.sdk.EventLogging
import org.json4s.JValue

trait TrustCodeBase extends EventLogging {
  def put(id: String, state: JValue): Unit
  def get(id: String): Unit
}

abstract class TrustCode extends TrustCodeBase {
  override def put(id: String, state: JValue): Unit = log(state).withCustomerId(id).commitAsync
  override def get(id: String): Unit = ???

}
