package com.ubirch.services.kafka.producer

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ProducerConfPaths
import com.ubirch.kafka.producer.StringProducer
import com.ubirch.models.EnrichedEventLog.enrichedEventLog
import com.ubirch.models.Error
import com.ubirch.util.ProducerRecordHelper
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

  import com.ubirch.models.EnrichedError._

  object Types {

    implicit def fromError(error: Error) = new ReporterMagnet {

      override type Result = Future[Option[RecordMetadata]]

      override def apply(): Result = {
        //logger.debug("Reporting error [{}]", error.toString)

        val eventLog = error.toEventLog(topic).sign(config)

        val record = ProducerRecordHelper.toRecord(
          topic,
          eventLog.id,
          eventLog.toJson,
          Map.empty
        )

        stringProducer
          .send(record)
          .map(x => Option(x)).recover {
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
