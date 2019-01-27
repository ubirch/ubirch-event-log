package com.ubirch.services.kafka.producer

import com.typesafe.config.Config
import com.ubirch.ConfPaths
import com.ubirch.models.{ Error, Event, EventLog }
import com.ubirch.util.{ ProducerRecordHelper, ToJson }
import javax.inject._

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
class Reporter @Inject() (producerManager: StringProducer, config: Config) {

  import ConfPaths.Producer._

  val topic: String = config.getString(ERROR_TOPIC_PATH)

  object Types {

    implicit def fromError(error: Error) = new ReporterMagnet {

      override type Result = Unit

      override def apply(): Result = {
        val payload = ToJson[Error](error).get
        val eventLog = EventLog(Event(getClass.getName, topic, payload))
        producerManager.producer.send(
          ProducerRecordHelper.toRecord(
            topic,
            error.id.toString,
            eventLog.toString,
            Map.empty
          )
        ).get()
      }

    }
  }

  def report(magnet: ReporterMagnet): magnet.Result = magnet()

}
