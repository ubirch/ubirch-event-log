package com.ubirch.services.kafka.producer

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.kafka.producer.{ Configs, StringProducer }
import com.ubirch.kafka.util.Exceptions.ProducerCreationException
import com.ubirch.kafka.util.{ NameGiver, PortGiver }
import com.ubirch.models.{ Error, EventLog }
import com.ubirch.util.{ EventLogJsonSupport, ProducerRecordHelper }
import com.ubirch.{ Entities, TestBase }
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.scalatest.mockito.MockitoSugar

class StringProducerSpec extends TestBase with MockitoSugar with LazyLogging {

  "StringProducerSpec" must {

    "not be created when props are empty" in {

      lazy val producer = StringProducer(Map.empty)

      assertThrows[IllegalArgumentException](producer)

    }

    "not be created when props are empty 2" in {

      val producer = new StringProducer {}
      producer.setProps(Map.empty)
      producer.setKeySerializer(Some(new StringSerializer()))
      producer.setValueSerializer(Some(new StringSerializer()))

      assertThrows[ProducerCreationException](producer.start)

    }

    "error message successfully pushed" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val configs = Configs(bootstrapServers = "localhost:" + kafkaConfig.kafkaPort)
      val topic = NameGiver.giveMeAnErrorTopicName

      val error = Entities.Errors.errorExample()
      val message = EventLog(topic, topic, EventLogJsonSupport.ToJson[Error](error).get)

      withRunningKafka {

        val stringProducer = StringProducer(configs)
        stringProducer.start

        stringProducer.getProducer
          .send(ProducerRecordHelper.toRecord(
            topic,
            message.id,
            message.toJson,
            Map.empty
          ))
          .get()

        consumeFirstStringMessageFrom(topic) mustBe message.toJson

        stringProducer.getProducerAsOpt.foreach(_.close())

      }

    }

  }

}
