package com.ubirch.services.kafka.producer

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ProducerConfPaths
import com.ubirch.kafka.producer.StringProducer
import com.ubirch.models.EnrichedEventLog.enrichedEventLog
import com.ubirch.models.{ Error, EventLog }
import com.ubirch.util.{ EventLogJsonSupport, ProducerRecordHelper }
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

@Singleton
class Reporter @Inject() (stringProducer: StringProducer, config: Config)(implicit ec: ExecutionContext) extends ProducerConfPaths with LazyLogging {

  val topic: String = config.getString(ERROR_TOPIC_PATH)

  object Types {

    implicit def fromError(error: Error) = new ReporterMagnet {

      override type Result = Future[Option[RecordMetadata]]

      override def apply(): Result = {
        //logger.debug("Reporting error [{}]", error.toString)

        val payload = EventLogJsonSupport.ToJson[Error](error).get

        val eventLog = EventLog(getClass.getName, topic, payload)
          .withIdAsCustomerId
          .sign(config)

        val record = ProducerRecordHelper.toRecord(
          topic,
          error.id.toString,
          eventLog.toJson,
          Map.empty
        )

        val futureResp = stringProducer.send(record).map(x => Option(x))

        futureResp.recover {
          case e: Exception =>
            logger.error("Error Reporting Error 0: {}", topic)
            logger.error("Error Reporting Error 1: {}", error)
            logger.error("Error Reporting Error 2: ", e)
            throw e
        }

      }

    }
  }

  def report(magnet: ReporterMagnet): magnet.Result = magnet()

}
