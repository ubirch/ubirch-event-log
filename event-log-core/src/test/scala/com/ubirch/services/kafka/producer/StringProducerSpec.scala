package com.ubirch.services.kafka.producer

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.{ Error, Event, EventLog }
import com.ubirch.services.kafka._
import com.ubirch.util.Implicits.configsToProps
import com.ubirch.util.{ ProducerRecordHelper, ToJson }
import com.ubirch.{ Entities, TestBase }
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.scalatest.mockito.MockitoSugar

class StringProducerSpec extends TestBase with MockitoSugar with LazyLogging {

  "StringProducerSpec" must {

    "not be created when props are empty" in {

      lazy val producer = new StringProducer(Map.empty)

      assertThrows[IllegalArgumentException](producer)

    }

    //TODO Needs to be updated with wrapped error
    "error message successfully pushed" in {

      implicit val config = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val configs = Configs(bootstrapServers = "localhost:" + config.kafkaPort)
      val topic = NameGiver.giveMeAnErrorTopicName

      val error = Entities.Errors.errorExample()
      val message = EventLog(Event(topic, topic, ToJson[Error](error).get))

      withRunningKafka {

        val stringProducer = new StringProducer(configs)

        stringProducer.producer
          .send(ProducerRecordHelper.toRecord(
            topic,
            message.event.id.toString,
            message.toString,
            Map.empty
          ))
          .get()

        consumeFirstStringMessageFrom(topic) mustBe message.toString

        stringProducer.producer.close()

      }

    }

  }

}
