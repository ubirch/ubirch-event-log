package com.ubirch.services.metrics

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.PrometheusConfPaths
import com.ubirch.kafka.metrics.PrometheusMetricsHelper
import com.ubirch.services.lifeCycle.Lifecycle
import io.prometheus.client.exporter.HTTPServer
import javax.inject._

import scala.concurrent.Future

@Singleton
class PrometheusMetrics @Inject() (config: Config, lifecycle: Lifecycle) extends PrometheusConfPaths with LazyLogging {

  val port: Int = config.getInt(PORT)

  logger.debug("Creating Prometheus Server on Port[{}]", port)

  val server: HTTPServer = PrometheusMetricsHelper.create(port)

  lifecycle.addStopHook { () =>
    logger.info("Shutting down Prometheus")
    Future.successful(server.stop())
  }

}
