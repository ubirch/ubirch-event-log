package com.ubirch.services.metrics

import java.net.BindException
import java.util.Random

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.PrometheusConfPaths
import com.ubirch.services.lifeCycle.Lifecycle
import io.prometheus.client.exporter.HTTPServer
import io.prometheus.client.hotspot.DefaultExports
import javax.inject._

import scala.concurrent.Future

@Singleton
class PrometheusMetrics @Inject() (config: Config, lifecycle: Lifecycle) extends PrometheusConfPaths with LazyLogging {

  DefaultExports.initialize()

  val port: Int = config.getInt(PORT)

  logger.debug("Creating Prometheus Server on Port[{}]", port)

  val server = try {
    new HTTPServer(port)
  } catch {
    case _: BindException =>
      val newPort = port + (new scala.util.Random()).nextInt(50)
      logger.debug("Port[{}] is busy, trying Port[{}]", port, newPort)
      new HTTPServer(newPort)
  }

  lifecycle.addStopHook { () =>
    logger.info("Shutting down Prometheus")
    Future.successful(server.stop())
  }

}
