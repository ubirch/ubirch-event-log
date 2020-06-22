package com.ubirch.chainer.services.tree

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ConsumerConfPaths
import com.ubirch.kafka.producer.StringProducer
import com.ubirch.models.EventLog
import com.ubirch.services.metrics.{ Counter, DefaultFailureCounter, DefaultSuccessCounter }
import com.ubirch.util.ProducerRecordHelper
import javax.inject._
import org.apache.kafka.clients.producer.RecordMetadata

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

class TreePublisher @Inject() (
    stringProducer: StringProducer,
    @Named(DefaultSuccessCounter.name) successCounter: Counter,
    @Named(DefaultFailureCounter.name) failureCounter: Counter,
    config: Config
)(implicit ec: ExecutionContext) extends LazyLogging {

  lazy val metricsSubNamespace: String = config.getString(ConsumerConfPaths.METRICS_SUB_NAMESPACE)

  def publish(topic: String, eventLog: EventLog): Future[RecordMetadata] = {
    val pr = ProducerRecordHelper.toRecordFromEventLog(topic, eventLog.id, eventLog)
    val futureSend = stringProducer.send(pr)
    futureSend.onComplete {
      case Success(_) =>
        successCounter.counter.labels(metricsSubNamespace).inc()
      case Failure(exception) =>
        logger.error("Error publishing tree", exception)
        failureCounter.counter.labels(metricsSubNamespace).inc()
    }
    futureSend

  }
}
