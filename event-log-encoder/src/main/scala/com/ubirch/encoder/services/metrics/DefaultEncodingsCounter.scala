package com.ubirch.encoder.services.metrics

import com.typesafe.config.Config
import com.ubirch.services.metrics.{ BasicPrometheusCounter, Counter }
import io.prometheus.client.{ Counter => PrometheusCounter }
import javax.inject._

@Singleton
class DefaultEncodingsCounter @Inject() (val config: Config) extends Counter with BasicPrometheusCounter {

  final val counter: PrometheusCounter =
    createCounter(
      namespace = metricsNamespace,
      name = "encodings_total",
      help = "Total encodings",
      labelNames = "result"
    ).register()

}

object DefaultEncodingsCounter {
  final val name = "DefaultEncodingsCounter"
}
