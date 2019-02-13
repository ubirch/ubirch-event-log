package com.ubirch

import com.ubirch.services.kafka.consumer.StringConsumer
import com.ubirch.util.Boot
import io.prometheus.client.hotspot.DefaultExports

/**
  * Represents an Event Log Service.
  * It starts an String Consumer that in turn starts all the rest of the
  * needed components, such as all the core components, executors, reporters, etc.
  */
object Service extends Boot {

  def main(args: Array[String]): Unit = {

    val consumer = get[StringConsumer]

    DefaultExports.initialize()

    import io.prometheus.client.exporter.HTTPServer
    val server = new HTTPServer(4321)

    //    val g = new Graphite("localhost", 2003)
    //     Push the default registry once.
    //    g.push(CollectorRegistry.defaultRegistry)

    // Push the default registry every 60 seconds.
    //    val thread = g.start(CollectorRegistry.defaultRegistry, 5)

    consumer.startPolling()

  }

}
