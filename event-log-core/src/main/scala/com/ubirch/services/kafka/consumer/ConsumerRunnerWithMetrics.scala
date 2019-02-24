package com.ubirch.services.kafka.consumer

import java.util.concurrent.atomic.AtomicReference

import org.joda.time.Instant
import com.ubirch.util.Implicits.enrichedInstant
import io.prometheus.client.{ Counter, Summary }

/**
  * Represents a Consumer Runner for a Kafka Consumer with metrics added to the prepoll and post commit callbacks
  * @param name Represents the Thread name
  * @tparam K Represents the type of the Key for the consumer.
  * @tparam V Represents the type of the Value for the consumer.
  */
abstract class ConsumerRunnerWithMetrics[K, V](name: String) extends ConsumerRunner[K, V](name) {

  final val pollSizeSummary: Summary = Summary.build
    .namespace("event_log")
    .name(s"consumer_${version.get()}_poll_size")
    .help("Poll size.")
    .register

  final val pollProcessLatencySummary: Summary = Summary.build
    .namespace("event_log")
    .name(s"consumer_${version.get()}_poll_process_seconds")
    .help("Poll process latency in seconds.")
    .register

  final val pausesCounter: Counter = Counter.build()
    .namespace("event_log")
    .name(s"consumer_${version.get()}_event_error_total")
    .help("Total event errors.")
    .labelNames("result")
    .register()

  val startInstant = new AtomicReference[Option[Instant]](None)
  val pollProcessTimer = new AtomicReference[Option[Summary.Timer]](None)

  onPrePoll(() => startInstant.set(Some(new Instant())))

  onPrePoll(() => pollProcessTimer.set(Some(pollProcessLatencySummary.startTimer)))

  onPostCommit { count =>
    val finishTime = new Instant()
    val seconds = startInstant.get().map(x => x.millisBetween(finishTime))
    logger.debug("Processed [{} records] in [{} millis]", count, seconds.map(_.toString).getOrElse("UNKNOWN"))
  }

  onPostCommit { count =>
    pollSizeSummary.observe(count)
    pollProcessTimer.get().map(x => x.observeDuration())
  }

  onNeedForPauseCallback(_ => pausesCounter.labels("NeedForPauseException").inc())

  onNeedForResumeCallback(() => pausesCounter.labels("NeedForResumeException").inc())

}
