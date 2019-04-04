package com.ubirch.services.kafka.producer

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ProducerConfPaths
import com.ubirch.kafka.producer.StringProducer
import com.ubirch.models.{ Error, EventLog }
import com.ubirch.util.{ EventLogJsonSupport, FutureHelper, ProducerRecordHelper }
import javax.inject._
import org.apache.kafka.clients.producer.RecordMetadata

import scala.concurrent.{ ExecutionContext, Future }
import scala.language.implicitConversions

/**
  * Magnet to support multiple errors messages on reporting.
  */
trait ReporterMagnet {

  type Result

  def apply(): Result

}

/**
  * Represent the singleton that is able to report stuff to the producer.
  * @param producerManager A kafka producer instance
  * @param config Represents the injected configuration component.
  */
@Singleton
class Reporter @Inject() (producerManager: StringProducer, config: Config)(implicit ec: ExecutionContext) extends ProducerConfPaths with LazyLogging {

  val futureHelper = new FutureHelper()

  val topic: String = config.getString(ERROR_TOPIC_PATH)

  object Types {

    implicit def fromError(error: Error) = new ReporterMagnet {

      override type Result = Future[RecordMetadata]

      override def apply(): Result = {
        //logger.debug("Reporting error [{}]", error.toString)

        val payload = EventLogJsonSupport.ToJson[Error](error).get
        //TODO: WHAT ID SHOULD WE PUT HERE???
        val eventLog = EventLog(getClass.getName, topic, payload)

        val record = ProducerRecordHelper.toRecord(
          topic,
          error.id.toString,
          eventLog.toString,
          Map.empty
        )

        val javaFutureSend = producerManager.getProducerOrCreate.send(record)

        futureHelper.fromJavaFuture(javaFutureSend)

      }

    }
  }

  def report(magnet: ReporterMagnet): magnet.Result = magnet()

}
