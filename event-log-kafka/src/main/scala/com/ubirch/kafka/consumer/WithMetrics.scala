package com.ubirch.kafka.consumer

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
  private var startInstant: Option[Instant] = None

  onPostPoll { count =>
    if (count > 0) startInstant = Some(new Instant())
  }

  onPostConsume { count =>
    if (count > 0) {
      val finishTime = new Instant()
      val seconds = startInstant.map(x => x.millisBetween(finishTime))
      logger.info("Processed [{} records] in [{} millis]", count, seconds.map(_.toString).getOrElse("UNKNOWN"))
    }
  }
  //Metrics Def End

  final val pollSizeSummary: Summary = Summary.build
    .namespace(metricsNamespace)
    .name(metricsName("processing_size_messages"))
    .help("Number of kafka messages received")
    .labelNames("service")
    .register

  private lazy final val pollConsumeLatencySummary: Summary = Summary.build
    .quantile(0.9, 0.05)
    .quantile(0.95, 0.05)
    .quantile(0.99, 0.05)
    .quantile(0.999, 0.05)
    .namespace(metricsNamespace)
    .name(metricsName("processing_time_seconds"))
    .help("Message processing time in seconds")
    .labelNames("service")
    .register

  private var pollConsumeTimer: Summary.Timer = _

  onPostPoll { count =>
    if (count > 0) {
      val requestTimer: Summary.Timer = pollConsumeLatencySummary
        .labels(metricsSubNamespaceLabel)
        .startTimer
      pollConsumeTimer = requestTimer
    }
  }

  onPostConsume { count =>
    if (count > 0) {
      pollSizeSummary.labels(metricsSubNamespaceLabel).observe(count)
      pollConsumeTimer.observeDuration()
    }
  }
  //Metrics Def End

  //Metrics Def Start
  lazy final val pausesCounter: Counter = Counter.build()
    .namespace(metricsNamespace)
    .name(metricsName("pauses_total"))
    .help("Total pauses/unpauses.")
    .labelNames("service", "result")
    .register()

  onNeedForPauseCallback(_ => pausesCounter.labels(metricsSubNamespaceLabel, "NeedForPauseException").inc())
  onNeedForResumeCallback(() => pausesCounter.labels(metricsSubNamespaceLabel, "NeedForResumeException").inc())
  //Metrics Def End

}
