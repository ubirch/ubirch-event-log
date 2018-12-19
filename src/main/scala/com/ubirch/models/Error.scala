package com.ubirch.models

import java.util.UUID

case class Error(id: UUID, message: String, exceptionName: String, value: String = "", serviceName: String = "event-log-service")
