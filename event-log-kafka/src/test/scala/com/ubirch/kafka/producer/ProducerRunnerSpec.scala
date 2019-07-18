package com.ubirch.kafka.producer

import com.ubirch.TestBase
import com.ubirch.kafka.util.Exceptions.ProducerCreationException
import com.ubirch.util.PortGiver
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.InvalidTopicException
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

    "topics handling collision" in {
      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      withRunningKafka {

        val config = Configs("localhost:" + kafkaConfig.kafkaPort)

        val producer = StringProducer(config)
        val record = new ProducerRecord[String, String]("test.works", "hola")
        val sent = producer.getProducerOrCreate.send(record).get()

        val record1 = new ProducerRecord[String, String]("test_works", "hola")

        val res = try {
          producer.getProducerOrCreate.send(record1).get()
          false
        } catch {
          case e: java.util.concurrent.ExecutionException =>
            e.getCause.isInstanceOf[InvalidTopicException]
          case _: Throwable => false
        }

        assert(res)

      }

    }

  }

}
