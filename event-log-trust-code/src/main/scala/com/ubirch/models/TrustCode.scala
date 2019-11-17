package com.ubirch.models

import com.ubirch.sdk.EventLogging
import org.json4s.JValue

import scala.concurrent.Future

trait TrustCodeBase extends EventLogging {
  def put(context: Context, state: JValue): Future[EventLog]
}

abstract class TrustCode extends TrustCodeBase {
  override def put(context: Context, state: JValue) =
    log(state)
      .withNewId(context.trustCodeId + ".TCE")
      .withCustomerId(context.ownerId)
      .withServiceClass("TRUST_CODE")
      .withCategory(Values.UPP_CATEGORY)
      .withCurrentEventTime
      .withRandomNonce
      .commitAsync

}

case class Context(trustCodeId: String, ownerId: String)

case class TrustCodeCreation(name: String, description: String, trustCode: String)

case class TrustCodeMethodParam(tpe: String, value: String)

case class TrustCodeResponse(id: String, endpoint: String, methods: List[String], eventLog: Option[EventLog])

case class TrustCodeSession(sessionId: String, trustCode: Class[TrustCode])
