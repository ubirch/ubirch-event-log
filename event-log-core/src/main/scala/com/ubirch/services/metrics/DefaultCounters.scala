package com.ubirch.services.metrics

import com.typesafe.config.Config
import com.ubirch.ConfPaths.ConsumerConfPaths
import com.ubirch.kafka.consumer.WithNamespace
import io.prometheus.client.{ Counter => PrometheusCounter }
import javax.inject._

trait Counter extends WithNamespace {

  val counter: PrometheusCounter

  def config: Config

  def metricsSubNamespaceLabel: String = config.getString(ConsumerConfPaths.METRICS_SUB_NAMESPACE)

}

trait BasicPrometheusCounter {

  def createCounter(namespace: String, name: String, help: String, labelNames: List[String]) = {
    PrometheusCounter.build()
      .namespace(namespace)
      .name(name)
      .help(help)
      .labelNames(labelNames: _*)
  }

}

@Singleton
class DefaultSuccessCounter @Inject() (val config: Config) extends Counter with BasicPrometheusCounter {

  final val counter: PrometheusCounter =
    createCounter(
      namespace = metricsNamespace,
      name = "successes_count",
      help = "Total Successes Counter",
      labelNames = List("service")
    ).register()

}

object DefaultSuccessCounter {
  final val name = "DefaultSuccessCounter"
}

@Singleton
class DefaultFailureCounter @Inject() (val config: Config) extends Counter with BasicPrometheusCounter {

  final val counter: PrometheusCounter =
    createCounter(
      namespace = metricsNamespace,
      name = "failures_count",
      help = "Total Failures Counter",
      labelNames = List("service")
    ).register()

}

object DefaultFailureCounter {
  final val name = "DefaultFailureCounter"
}
