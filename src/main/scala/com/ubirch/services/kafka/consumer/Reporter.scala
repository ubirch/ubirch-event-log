package com.ubirch.services.kafka.consumer

import com.ubirch.services.kafka.producer.StringProducer
import javax.inject._
import com.ubirch.models.Error
import scala.language.implicitConversions

trait ReporterMagnet {

  type Result

  def apply(): Result

}

class Reporter @Inject() (producerManager: StringProducer) {

  object Types {
    implicit def fromError(error: Error) = new ReporterMagnet {

      override type Result = Unit

      override def apply(): Result = ()

    }
  }

  def report(magnet: ReporterMagnet): magnet.Result = magnet()

}
