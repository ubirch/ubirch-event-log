package com.ubirch.discovery.services.metrics

import com.typesafe.config.Config
import com.ubirch.services.metrics.{ BasicPrometheusCounter, Counter }
import javax.inject._
import io.prometheus.client.{ Counter => PrometheusCounter }

@Singleton
class DefaultEventsCounter @Inject() (val config: Config) extends Counter with BasicPrometheusCounter {

  final val counter: PrometheusCounter =
    createCounter(
      namespace = metricsNamespace,
      name = "events",
      help = "Total Events",
      labelNames = List("service", "result")
    ).register()

}

object DefaultEventsCounter {
  final val name = "DefaultEventsCounter"
}
