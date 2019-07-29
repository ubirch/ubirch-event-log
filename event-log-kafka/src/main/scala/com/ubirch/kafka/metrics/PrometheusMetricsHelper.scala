package com.ubirch.kafka.metrics

import java.net.BindException

import com.typesafe.scalalogging.LazyLogging
import io.prometheus.client.exporter.HTTPServer
import io.prometheus.client.hotspot.DefaultExports

object PrometheusMetricsHelper extends LazyLogging {

  final private val maxAttempts = 3

  def create(port: Int): HTTPServer = {
    DefaultExports.initialize()

    def go(attempts: Int, port: Int): HTTPServer = {
      try {
        new HTTPServer(port)
      } catch {
        case e: BindException =>
          val newPort = port + new scala.util.Random().nextInt(50)
          logger.debug("Attempt[{}], Port[{}] is busy, trying Port[{}]", attempts, port, newPort)
          if (attempts == maxAttempts) {
            throw e
          } else {
            go(attempts + 1, newPort)
          }
      }
    }

    val server = go(0, port)
    logger.debug(s"You can visit http://localhost:${server.getPort}/ to check the metrics.")
    server
  }

}
