package com.ubirch.services.kafka

import net.manub.embeddedkafka.EmbeddedKafkaConfig

class KafkaServiceSpec extends TestBase {

  "Kafka Service" must {

    "hello" in {

      withRunningKafka {

      }

    }

  }

}