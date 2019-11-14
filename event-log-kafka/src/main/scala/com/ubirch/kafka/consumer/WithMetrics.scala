package com.ubirch.kafka.consumer

import java.util.concurrent.atomic.AtomicReference

import com.ubirch.models.Values
import com.ubirch.util.Implicits.enrichedInstant
import io.prometheus.client.{ Counter, Summary }
import org.joda.time.Instant

trait WithNamespace {

  private[consumer] def simpleName = true

  def metricsSubNamespaceLabel: String
  def metricsNamespace: String = if (simpleName) "" else Values.UBIRCH + "_" + metricsSubNamespaceLabel
}

/**
  * Decorates a ConsumerRunner with prometheus metrics added to the prepoll and post commit callbacks
  * The corresponding scrapper must be started.
  */
trait WithMetrics extends WithNamespace {

  cr: ConsumerRunner[_, _] =>

  def metricsName(name: String): String = if (simpleName) name else s"consumer_${version.get()}_$name"

  //Metrics Def Start
  val startInstant = new AtomicReference[Option[Instant]](None)

  onPostPoll(() => startInstant.set(Some(new Instant())))
  onPostConsume { count =>
    if (count > 0) {
      val finishTime = new Instant()
      val seconds = startInstant.get().map(x => x.millisBetween(finishTime))
      logger.info("Processed [{} records] in [{} millis]", count, seconds.map(_.toString).getOrElse("UNKNOWN"))
    }
  }
  //Metrics Def End

  //Metrics Def Start
  //  final val pollSizeSummary: Summary = Summary.build
  //    .namespace(metricsNamespace)
  //    .name(metricsName("poll_consume_size"))
  //    .help("Poll consume size.")
  //    .labelNames("service")
  //    .register

  final val pollConsumeLatencySummary: Summary = Summary.build
    .namespace(metricsNamespace)
    .name(metricsName("processing_time"))
    .help("Message processing time in seconds")
    .labelNames("service")
    .register

  val pollConsumeTimer = new AtomicReference[Option[Summary.Timer]](None)

  onPostPoll(() => pollConsumeTimer.set(Some(pollConsumeLatencySummary.labels(metricsSubNamespaceLabel).startTimer)))
  onPostConsume { count =>
    pollConsumeLatencySummary.labels(metricsSubNamespaceLabel).observe(count)
    pollConsumeTimer.get().map(x => x.observeDuration())
  }
  //Metrics Def End

  //Metrics Def Start
  final val pausesCounter: Counter = Counter.build()
    .namespace(metricsNamespace)
    .name(metricsName("pauses_total"))
    .help("Total pauses/unpauses.")
    .labelNames("service", "result")
    .register()

  onNeedForPauseCallback(_ => pausesCounter.labels(metricsSubNamespaceLabel, "NeedForPauseException").inc())
  onNeedForResumeCallback(() => pausesCounter.labels(metricsSubNamespaceLabel, "NeedForResumeException").inc())
  //Metrics Def End

}
