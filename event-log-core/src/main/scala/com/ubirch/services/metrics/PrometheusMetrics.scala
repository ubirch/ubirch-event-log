package com.ubirch.services.metrics

import java.net.BindException

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.services.lifeCycle.Lifecycle
import io.prometheus.client.exporter.HTTPServer
import io.prometheus.client.hotspot.DefaultExports
import javax.inject._

import scala.concurrent.Future

@Singleton
class PrometheusMetrics @Inject() (config: Config, lifecycle: Lifecycle) extends LazyLogging {

  import com.ubirch.ConfPaths.Prometheus._

  DefaultExports.initialize()

  val port: Int = config.getInt(PORT)

  logger.debug("Creating Prometheus Server on Port[{}]", port)

  val server = try {
    new HTTPServer(port)
  } catch {
    case _: BindException =>
      val newPort = port + 1
      logger.debug("Port[{}] is busy, trying Port[{}]", port, newPort)
      new HTTPServer(newPort)
  }

  lifecycle.addStopHook { () =>
    logger.info("Shutting down Prometheus")
    Future.successful(server.stop())
  }

}
