package com.ubirch.services.metrics

import io.prometheus.client.{ Counter => PrometheusCounter }
import javax.inject._

trait Counter {
  val namespace: String
  val counter: PrometheusCounter
}

@Singleton
class DefaultConsumerRecordsManagerCounter extends Counter {

  val namespace: String = "ubirch"

  final val counter: PrometheusCounter = PrometheusCounter.build()
    .namespace(namespace)
    .name("event_error_total")
    .help("Total event errors.")
    .labelNames("result")
    .register()

}
