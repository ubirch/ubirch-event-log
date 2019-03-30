package com.ubirch.services.kafka.producer

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.TestBase
import com.ubirch.kafka.producer.StringProducer
import org.scalatest.mockito.MockitoSugar

class ReporterSpec extends TestBase with MockitoSugar with LazyLogging {

  //TODO: ADD SPECS FOR REPORTER
  "ReporterSpec" must {

    "not be created when props are empty" in {

      lazy val producer = StringProducer(Map.empty)

      assertThrows[IllegalArgumentException](producer)

    }

  }

}
