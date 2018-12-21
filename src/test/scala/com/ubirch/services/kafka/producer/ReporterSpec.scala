package com.ubirch.services.kafka.producer

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.services.kafka._
import org.scalatest.mockito.MockitoSugar

class ReporterSpec extends TestBase with MockitoSugar with LazyLogging {

  "ReporterSpec" must {

    "not be created when props are empty" in {

      lazy val producer = new StringProducer(Map.empty)

      assertThrows[IllegalArgumentException](producer)

    }

  }

}
