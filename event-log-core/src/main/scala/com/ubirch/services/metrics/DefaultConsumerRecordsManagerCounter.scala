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

@Singleton
class DefaultConsumerRecordsManagerCounter @Inject() (val config: Config) extends Counter {

  final val counter: PrometheusCounter = PrometheusCounter.build()
    .namespace(metricsNamespace)
    .name("event_error_total")
    .help("Total event errors.")
    .labelNames("result")
    .register()

}

@Singleton
class DefaultMetricsLoggerCounter @Inject() (val config: Config) extends Counter {

  final val counter: PrometheusCounter = PrometheusCounter.build()
    .namespace(metricsNamespace)
    .name("events_total")
    .help("Total events.")
    .labelNames("result")
    .register()

}
