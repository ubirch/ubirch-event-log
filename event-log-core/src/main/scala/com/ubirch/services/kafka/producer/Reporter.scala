package com.ubirch.services.kafka.producer

import com.typesafe.config.Config
import com.ubirch.ConfPaths
import com.ubirch.models.Error
import com.ubirch.util.{ ProducerRecordHelper, ToJson }
import javax.inject._

import scala.language.implicitConversions

trait ReporterMagnet {

  type Result

  def apply(): Result

}

@Singleton
class Reporter @Inject() (producerManager: StringProducer, config: Config) {

  import ConfPaths.Producer._

  val topic: String = config.getString(ERROR_TOPIC_PATH)

  object Types {
    implicit def fromError(error: Error) = new ReporterMagnet {

      override type Result = Unit

      override def apply(): Result = {
        val payload = ToJson[Error](error).toString
        // TODO put error in event log structure
        producerManager.producer.send(ProducerRecordHelper.toRecord(topic, error.id.toString, payload, Map.empty)).get()
      }

    }
  }

  def report(magnet: ReporterMagnet): magnet.Result = magnet()

}
