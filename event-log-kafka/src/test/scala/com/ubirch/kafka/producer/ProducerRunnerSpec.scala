package com.ubirch.kafka.producer

import com.ubirch.TestBase
import com.ubirch.kafka.util.Exceptions.ProducerCreationException
import org.apache.kafka.common.serialization.StringSerializer

class ProducerRunnerSpec extends TestBase {

  "Producer Runner " must {

    "fail if no serializers have been set" in {

      val producer = ProducerRunner[String, String](Map.empty, None, None)

      assertThrows[ProducerCreationException](producer.start)

      val producer1 = ProducerRunner[String, String](Map.empty, Option(new StringSerializer), None)

      assertThrows[ProducerCreationException](producer1.start)

      val producer2 = ProducerRunner[String, String](Map.empty, None, Option(new StringSerializer))

      assertThrows[ProducerCreationException](producer2.start)

    }

    "fail if props are empty" in {
      val producer = ProducerRunner[String, String](Map.empty, Option(new StringSerializer), Option(new StringSerializer))

      assertThrows[ProducerCreationException](producer.start)

    }

  }

}
