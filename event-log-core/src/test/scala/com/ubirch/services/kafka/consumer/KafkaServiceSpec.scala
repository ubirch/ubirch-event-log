package com.ubirch.services.kafka.consumer

import com.ubirch.TestBase
import com.ubirch.util.PortGiver
import net.manub.embeddedkafka.EmbeddedKafkaConfig

class KafkaServiceSpec extends TestBase {

  "Kafka Service" must {

    "should respond to simple publish/subscribe test" in {

      implicit val config: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      withRunningKafka {
        publishStringMessageToKafka("topic", "message")
        consumeFirstStringMessageFrom("topic") mustBe "message"
      }

    }

  }

}
