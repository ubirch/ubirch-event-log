package com.ubirch.services.kafka.consumer

import java.util.concurrent.atomic.AtomicReference

import org.joda.time.Instant

import com.ubirch.util.Implicits.enrichedInstant
import io.prometheus.client.Summary

/**
  * Represents a Consumer Runner for a Kafka Consumer with metrics added to the prepoll and post commit callbacks
  * @param name Represents the Thread name
  * @tparam K Represents the type of the Key for the consumer.
  * @tparam V Represents the type of the Value for the consumer.
  */
abstract class ConsumerRunnerWithMetrics[K, V](name: String) extends ConsumerRunner[K, V](name) {

  val pollSize: Summary = Summary.build
    .namespace("event_log")
    .name("poll_size")
    .help("Poll size.")
    .register

  val pollProcessLatency: Summary = Summary.build
    .namespace("event_log")
    .name("poll_process_seconds")
    .help("Poll process latency in seconds.")
    .register

  val startInstant = new AtomicReference[Option[Instant]](None)
  val pollProcessTimer = new AtomicReference[Option[Summary.Timer]](None)

  onPrePoll(() => startInstant.set(Some(new Instant())))
  onPrePoll(() => pollProcessTimer.set(Some(pollProcessLatency.startTimer)))

  onPostCommit { count =>
    val finishTime = new Instant()
    val seconds = startInstant.get().map(x => x.millisBetween(finishTime))
    logger.debug("Polled and Committed ... [{} records] ... [{} millis]", count, seconds.map(_.toString).getOrElse("UNKNOWN"))
  }

  onPostCommit { count =>
    pollSize.observe(count)
    pollProcessTimer.get().map(x => x.observeDuration())
  }

}
