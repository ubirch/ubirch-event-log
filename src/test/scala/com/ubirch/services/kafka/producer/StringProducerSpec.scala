package com.ubirch.services.kafka.producer

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.Error
import com.ubirch.services.kafka._
import com.ubirch.util.Implicits.configsToProps
import com.ubirch.util.ToJson
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.scalatest.mockito.MockitoSugar

class StringProducerSpec extends TestBase with MockitoSugar with LazyLogging {

  "StringProducerSpec" must {

    "not be created when props are empty" in {

      lazy val producer = new StringProducer(Map.empty)

      assertThrows[IllegalArgumentException](producer)

    }

    "error message successfully pushed" in {

      val error = Entities.Errors.errorExample()

      val payload = ToJson[Error](error).toString
      val me = MessageEnvelope(payload)

      implicit val config = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val configs = Configs(bootstrapServers = "localhost:" + config.kafkaPort)
      val topic = NameGiver.giveMeAnErrorTopicName

      withRunningKafka {

        new StringProducer(configs)
          .producer
          .send(MessageEnvelope.toRecord(topic, error.id.toString, me))
          .get()

        consumeFirstStringMessageFrom(topic) mustBe payload

      }

    }

  }

}
