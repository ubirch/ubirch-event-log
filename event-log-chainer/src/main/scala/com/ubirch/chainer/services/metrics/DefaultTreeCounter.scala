package com.ubirch.chainer.services.metrics

import com.typesafe.config.Config
import com.ubirch.services.metrics.{ BasicPrometheusCounter, Counter }
import io.prometheus.client.{ Counter => PrometheusCounter }
import javax.inject._

@Singleton
class DefaultTreeCounter @Inject() (val config: Config) extends Counter with BasicPrometheusCounter {

  final val counter: PrometheusCounter =
    createCounter(
      namespace = metricsNamespace,
      name = "tree_total",
      help = "Total Trees",
      labelNames = "result"
    ).register()

}

object DefaultTreeCounter {
  final val name = "DefaultTreeCounter"
}

@Singleton
class DefaultLeavesCounter @Inject() (val config: Config) extends Counter with BasicPrometheusCounter {

  final val counter: PrometheusCounter =
    createCounter(
      namespace = metricsNamespace,
      name = "leaves_total",
      help = "Total Leaves",
      labelNames = "result"
    ).register()

}

object DefaultLeavesCounter {
  final val name = "DefaultLeavesCounter"
}

