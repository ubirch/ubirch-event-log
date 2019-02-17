package com.ubirch.services.kafka.producer

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths
import com.ubirch.models.{ Error, EventLog }
import com.ubirch.util.{ JavaFutureHelper, ProducerRecordHelper, ToJson }
import javax.inject._
import org.apache.kafka.clients.producer.RecordMetadata

import scala.concurrent.Future
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
class Reporter @Inject() (producerManager: StringProducer, config: Config) extends LazyLogging {

  import ConfPaths.Producer._

  val topic: String = config.getString(ERROR_TOPIC_PATH)

  object Types {

    implicit def fromError(error: Error) = new ReporterMagnet {

      override type Result = Future[RecordMetadata]

      override def apply(): Result = {
        logger.debug("Reporting error [{}]", error.toString)

        val payload = ToJson[Error](error).get
        val eventLog = EventLog(getClass.getName, topic, payload)

        val record = ProducerRecordHelper.toRecord(
          topic,
          error.id.toString,
          eventLog.toString,
          Map.empty
        )

        val javaFutureSend = producerManager.producer.send(record)

        JavaFutureHelper.toScalaFuture(javaFutureSend)

      }

    }
  }

  def report(magnet: ReporterMagnet): magnet.Result = magnet()

}
