package com.ubirch.kafka.consumer

import java.util.concurrent.atomic.AtomicReference

import com.ubirch.models.Values
import com.ubirch.util.Implicits.enrichedInstant
import io.prometheus.client.{ Counter, Summary }
import org.joda.time.Instant

trait WithNamespace {
  def metricsSubNamespaceLabel: String
  def metricsNamespace: String = Values.UBIRCH + "_" + metricsSubNamespaceLabel
}

/**
  * Decorates a ConsumerRunner with prometheus metrics added to the prepoll and post commit callbacks
  * The corresponding scrapper must be started.
  * @tparam K Represents the type of the Key for the consumer.
  * @tparam V Represents the type of the Value for the consumer.
  */
trait WithMetrics[K, V] extends WithNamespace {

  cr: ConsumerRunner[K, V] =>

  def metricsName(name: String): String = s"consumer_${version.get()}_$name"

  //Metrics Def Start
  val startInstant = new AtomicReference[Option[Instant]](None)

  onPreConsume(() => startInstant.set(Some(new Instant())))
  onPostConsume { count =>
    if (count > 0) {
      val finishTime = new Instant()
      val seconds = startInstant.get().map(x => x.millisBetween(finishTime))
      logger.debug("Processed [{} records] in [{} millis]", count, seconds.map(_.toString).getOrElse("UNKNOWN"))
    }
  }
  //Metrics Def End

  //Metrics Def Start
  final val pollSizeSummary: Summary = Summary.build
    .namespace(metricsNamespace)
    .name(metricsName("poll_consume_size"))
    .help("Poll consume size.")
    .register

  final val pollConsumeLatencySummary: Summary = Summary.build
    .namespace(metricsNamespace)
    .name(metricsName("poll_consume_seconds"))
    .help("Poll consume latency in seconds.")
    .register

  val pollConsumeTimer = new AtomicReference[Option[Summary.Timer]](None)

  onPreConsume(() => pollConsumeTimer.set(Some(pollConsumeLatencySummary.startTimer)))
  onPostConsume { count =>
    pollSizeSummary.observe(count)
    pollConsumeTimer.get().map(x => x.observeDuration())
  }
  //Metrics Def End

  //Metrics Def Start
  final val pausesCounter: Counter = Counter.build()
    .namespace(metricsNamespace)
    .name(metricsName("event_error_total"))
    .help("Total event errors.")
    .labelNames("result")
    .register()

  onNeedForPauseCallback(_ => pausesCounter.labels("NeedForPauseException").inc())
  onNeedForResumeCallback(() => pausesCounter.labels("NeedForResumeException").inc())
  //Metrics Def End

}
