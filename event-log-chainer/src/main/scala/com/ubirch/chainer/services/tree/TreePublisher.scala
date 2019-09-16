package com.ubirch.chainer.services.tree

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ConsumerConfPaths
import com.ubirch.kafka.producer.StringProducer
import com.ubirch.models.{ EventLog, Values }
import com.ubirch.services.metrics.{ Counter, DefaultMetricsLoggerCounter }
import com.ubirch.util.ProducerRecordHelper
import javax.inject._
import org.apache.kafka.clients.producer.RecordMetadata

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

class TreePublisher @Inject() (
    stringProducer: StringProducer,
    @Named(DefaultMetricsLoggerCounter.name) counter: Counter,
    config: Config
)(implicit ec: ExecutionContext) extends LazyLogging {

  lazy val metricsSubNamespace: String = config.getString(ConsumerConfPaths.METRICS_SUB_NAMESPACE)

  def publish(topic: String, eventLog: EventLog): Future[RecordMetadata] = {
    val pr = ProducerRecordHelper.toRecordFromEventLog(topic, eventLog.id.toString, eventLog)
    val futureSend = stringProducer.send(pr)
    futureSend.onComplete {
      case Success(_) =>
        counter.counter.labels(metricsSubNamespace, Values.SUCCESS).inc()
      case Failure(exception) =>
        logger.error("Error publishing ", exception)
        counter.counter.labels(metricsSubNamespace, Values.FAILURE).inc()
    }
    futureSend

  }
}
