package com.ubirch.model

import java.util.{Date, UUID}

import com.ubirch.models.{EventBase, EventMsgBase}

case class ExampleEvent(id: UUID, eventTime: Date, category: String, serviceClass: String, clientId: UUID, deviceId: UUID)
  extends EventBase

case class ExampleEventMsg(event: ExampleEvent, signature: String)
  extends EventMsgBase
