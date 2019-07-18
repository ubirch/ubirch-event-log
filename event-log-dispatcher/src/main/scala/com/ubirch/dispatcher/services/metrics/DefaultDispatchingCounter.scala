package com.ubirch.dispatcher.services.metrics

import com.typesafe.config.Config
import com.ubirch.services.metrics.{ BasicPrometheusCounter, Counter }
import io.prometheus.client.{ Counter => PrometheusCounter }
import javax.inject._

@Singleton
class DefaultDispatchingCounter @Inject() (val config: Config) extends Counter with BasicPrometheusCounter {

  final val counter: PrometheusCounter =
    createCounter(
      namespace = metricsNamespace,
      name = "dispatch_total",
      help = "Total Dispatches",
      labelNames = "result"
    ).register()

}

object DefaultDispatchingCounter {
  final val name = "DefaultDispatchingCounter"
}

