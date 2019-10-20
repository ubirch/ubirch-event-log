package com.ubirch.models

case class TrustCodeCreation(name: String, description: String, trustCode: String)

case class TrustCodeResponse(id: String, endpoint: String, eventLog: Option[EventLog])

