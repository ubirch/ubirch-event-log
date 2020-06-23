package com.ubirch.verification.service.util

import com.google.inject.Provider
import com.typesafe.config.Config
import com.ubirch.verification.service.util.udash.JettyServer
import com.ubirch.verification.service.{Api, ApiImpl}
import io.udash.rest.openapi.{Info, Server}
import javax.inject._

@Singleton
class JettyServerProvider @Inject()(apiImpl: ApiImpl, config: Config) extends Provider[JettyServer] {

  private val verificationConfig = config.getConfig("verification")

  private val openApi = Api.openapiMetadata.openapi(
    Info("Verification Microservice", "1.0.0",
      description = "Verification Micro-Service with a REST endpoint that accesses Log Service"),
    servers = List(Server(verificationConfig.getString("swaggerBaseUrl")))
  )

  private val jettyServer = new JettyServer(apiImpl, openApi, verificationConfig.getInt("http.port"))

  override def get(): JettyServer = jettyServer

}
