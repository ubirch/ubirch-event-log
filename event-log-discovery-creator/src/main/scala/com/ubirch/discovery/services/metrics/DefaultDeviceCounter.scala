package com.ubirch.discovery.services.metrics

import com.typesafe.config.Config
import com.ubirch.services.metrics.{ BasicPrometheusCounter, Counter }
import io.prometheus.client.{ Counter => PrometheusCounter }
import javax.inject._

//"service", "device-id"
//TODO This counter should be discarded in favor of proper data bucket for managing stats.
//Prometheuos recommends not to use labels as an unbounded collection
@Singleton
class DefaultDeviceCounter @Inject() (val config: Config) extends Counter with BasicPrometheusCounter {

  final val counter: PrometheusCounter =
    createCounter(
      namespace = metricsNamespace,
      name = "event_log_devices",
      help = "Devices Found",
      labelNames = List("service", "device_id")
    ).register()

}

object DefaultDeviceCounter {
  final val name = "DefaultDeviceCounter"
}
