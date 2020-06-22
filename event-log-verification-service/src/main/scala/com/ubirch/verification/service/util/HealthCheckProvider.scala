package com.ubirch.verification.service.util

import com.typesafe.config.Config
import com.ubirch.niomon.healthcheck.{Checks, HealthCheckServer}
import javax.inject._

@Singleton
class HealthCheckProvider @Inject()(config: Config) extends Provider[HealthCheckServer] {

  override def get(): HealthCheckServer = initHealthchecks(config.getConfig("verification"))

  private def initHealthchecks(config: Config): HealthCheckServer = {
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
