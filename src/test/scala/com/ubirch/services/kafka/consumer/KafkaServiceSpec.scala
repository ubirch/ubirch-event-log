package com.ubirch.services.kafka.consumer

import com.ubirch.services.kafka.TestBase

class KafkaServiceSpec extends TestBase {

  "Kafka Service" must {

    "should respond to simple publish/subscribe test" in {

      withRunningKafka {
        publishStringMessageToKafka("topic", "message")
        consumeFirstStringMessageFrom("topic") mustBe "message"
      }

    }

  }

}