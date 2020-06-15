package com.ubirch.chainer.services.metrics

import com.typesafe.config.Config
import com.ubirch.services.metrics.{ BasicPrometheusCounter, Counter }
import io.prometheus.client.{ Counter => PrometheusCounter }
import javax.inject._

/**
  * Represents a Prometheus Counter for the generated Trees
  * @param config Represents the configuration object
  */
@Singleton
class DefaultTreeCounter @Inject() (val config: Config) extends Counter with BasicPrometheusCounter {

  final val counter: PrometheusCounter =
    createCounter(
      namespace = metricsNamespace,
      name = "tree_total",
      help = "Total Trees",
      labelNames = List("service", "result")
    ).register()

}

/**
  * Companion object for the Tree Counter
  */
object DefaultTreeCounter {
  final val name = "DefaultTreeCounter"
}

/**
  * Represents a Prometheus counter for the number of leaves created or received
  * @param config Represents the configuration object
  */
@Singleton
class DefaultLeavesCounter @Inject() (val config: Config) extends Counter with BasicPrometheusCounter {

  final val counter: PrometheusCounter =
    createCounter(
      namespace = metricsNamespace,
      name = "leaves_total",
      help = "Total Leaves",
      labelNames = List("service", "result")
    ).register()

}

/**
  * Companion object for the Leaves Counter
  */
object DefaultLeavesCounter {
  final val name = "DefaultLeavesCounter"
}

