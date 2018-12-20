package com.ubirch.services.kafka.producer

import com.typesafe.config.Config
import com.ubirch.ConfPaths
import com.ubirch.models.Error
import com.ubirch.services.kafka.MessageEnvelope
import com.ubirch.util.ToJson
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
        val me = MessageEnvelope(payload)
        producerManager.producer.send(MessageEnvelope.toRecord(topic, error.id.toString, me)).get()
      }

    }
  }

  def report(magnet: ReporterMagnet): magnet.Result = magnet()

}
