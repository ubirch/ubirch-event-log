package com.ubirch.discovery.services.metrics

import com.typesafe.config.Config
import com.ubirch.services.metrics.{ BasicPrometheusCounter, Counter }
import io.prometheus.client.{ Counter => PrometheusCounter }
import javax.inject._

@Singleton
class DefaultDeviceCounter @Inject() (val config: Config) extends Counter with BasicPrometheusCounter {

  final val counter: PrometheusCounter =
    createCounter(
      namespace = metricsNamespace,
      name = "event_log_devices",
      help = "Devices Found",
      labelNames = List("service")
    ).register()

}

object DefaultDeviceCounter {
  final val name = "DefaultDeviceCounter"
}
