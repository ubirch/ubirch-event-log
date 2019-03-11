package com.ubirch.services.kafka.producer

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.kafka.producer.Configs
import com.ubirch.kafka.util.Exceptions.ProducerCreationException
import com.ubirch.models.{ Error, EventLog }
import com.ubirch.util.{ EventLogJsonSupport, NameGiver, PortGiver, ProducerRecordHelper }
import com.ubirch.{ Entities, TestBase }
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.scalatest.mockito.MockitoSugar

class StringProducerSpec extends TestBase with MockitoSugar with LazyLogging {

  "StringProducerSpec" must {

    "not be created when props are empty" in {

      lazy val producer = StringProducer(Map.empty, new StringSerializer(), new StringSerializer())

      assertThrows[IllegalArgumentException](producer)

    }

    "not be created when props are empty 2" in {

      val producer = new StringProducer
      producer.setProps(Map.empty)
      producer.setKeySerializer(Some(new StringSerializer()))
      producer.setValueSerializer(Some(new StringSerializer()))

      assertThrows[ProducerCreationException](producer.start)

    }

    "error message successfully pushed" in {

      implicit val config: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val configs = Configs(bootstrapServers = "localhost:" + config.kafkaPort)
      val topic = NameGiver.giveMeAnErrorTopicName

      val error = Entities.Errors.errorExample()
      val message = EventLog(topic, topic, EventLogJsonSupport.ToJson[Error](error).get)

      withRunningKafka {

        val stringProducer = StringProducer(configs, new StringSerializer(), new StringSerializer())
        stringProducer.start

        stringProducer.getProducer
          .send(ProducerRecordHelper.toRecord(
            topic,
            message.id.toString,
            message.toString,
            Map.empty
          ))
          .get()

        consumeFirstStringMessageFrom(topic) mustBe message.toString

        stringProducer.getProducerAsOpt.foreach(_.close())

      }

    }

  }

}
