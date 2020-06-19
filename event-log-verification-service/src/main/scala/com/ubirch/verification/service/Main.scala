package com.ubirch.verification.service

import com.typesafe.config.{Config, ConfigFactory}
import com.ubirch.niomon.healthcheck.{Checks, HealthCheckServer}
import com.ubirch.util.Boot
import com.ubirch.verification.service.services.LookupServiceBinder
import io.udash.rest.openapi.{Info, Server}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

object Main extends Boot(LookupServiceBinder.modules) {
  def main(args: Array[String]): Unit = {
    implicit val globalExec: ExecutionContextExecutor = ExecutionContext.global
    val config = ConfigFactory.load()
    val verificationConfig = config.getConfig("verification")

    val openApi = Api.openapiMetadata.openapi(
      Info("Event Log Verification Service", "1.0.0",
        description = "Verification Micro-Service with a REST endpoint that accesses Log Service"),
      servers = List(Server(verificationConfig.getString("swaggerBaseUrl")))
    )

    //    val redisCache = new RedisCache("verification", config)
    //    val healthcheck = initHealthchecks(verificationConfig)

    //    val cachedEventLog = new CachedEventLogClient(eventLog, redisCache)(ExecutionContext.global)
    //    val verifier = new KeyServiceBasedVerifier(new KeyServerClient(config))

    //      new JettyServer(new ApiImpl(cachedEventLog, verifier, redisCache, healthcheck), openApi, verificationConfig.getInt("http.port")).startAndJoin()
  }

  def initHealthchecks(config: Config): HealthCheckServer = {
    val s = new HealthCheckServer(Map(), Map())

    s.setLivenessCheck(Checks.notInitialized("business-logic"))
    s.setReadinessCheck(Checks.notInitialized("business-logic"))

    s.setReadinessCheck(Checks.notInitialized("kafka-consumer"))
    s.setReadinessCheck(Checks.notInitialized("kafka-producer"))

    if (config.getBoolean("health-check.enabled")) {
      s.run(config.getInt("health-check.port"))
    }

    s
  }
}
