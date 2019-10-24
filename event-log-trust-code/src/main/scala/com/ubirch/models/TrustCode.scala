package com.ubirch.models

import com.ubirch.sdk.EventLogging
import org.json4s.JValue

import scala.concurrent.Future

trait TrustCodeBase extends EventLogging {
  def put(id: String, state: JValue): Future[EventLog]
  def get(id: String): Unit
}

abstract class TrustCode extends TrustCodeBase {
  override def put(id: String, state: JValue) =
    log(state)
      .withNewId(id + ".TCE")
      .withCategory(Values.UPP_CATEGORY)
      .withCurrentEventTime
      .withRandomNonce
      .commitAsync
  override def get(id: String): Unit = ???

}

case class Context(id: String)

case class TrustCodeCreation(name: String, description: String, trustCode: String)

case class TrustCodeMethodParam(tpe: String, value: String)

case class TrustCodeResponse(id: String, endpoint: String, eventLog: Option[EventLog])

case class TrustCodeSession(sessionId: String, trustCode: Class[TrustCode])
