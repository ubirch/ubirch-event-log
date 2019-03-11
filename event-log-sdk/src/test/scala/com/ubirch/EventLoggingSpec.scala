package com.ubirch

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.kafka.producer.Configs
import com.ubirch.sdk.EventLogging
import com.ubirch.services.kafka.producer.StringProducer
import com.ubirch.util.ToJson
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.apache.kafka.common.serialization.StringSerializer
import org.scalatest.mockito.MockitoSugar

case class Hello(name: String)

class EventLoggingSpec extends TestBase with MockitoSugar with LazyLogging {

  "EventLogging" must {

    "log message" in {

      implicit val kafKaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      withRunningKafka {

        val logger = new EventLogging

        import logger._

        val configs = Configs(bootstrapServers = "localhost:" + kafKaConfig.kafkaPort)

        setStringProducer(StringProducer(configs, new StringSerializer(), new StringSerializer()))

        val logged = logger.log(Hello("Hello")).commit

        consumeFirstStringMessageFrom("com.ubirch.eventlog") mustBe logged.toString

        val logged2 = logger.log(ToJson("Hola").get).commit

        consumeFirstStringMessageFrom("com.ubirch.eventlog") mustBe logged2.toString

        val log1 = log(ToJson(Hello("Hola")).get, "My Category")

        val log2 = log(ToJson(Hello("Como estas")).get, "My another Category")

        //Let's unite them in order first in first out
        val log1_2 = log1 +> log2

        //Let's actually commit it
        log1_2.commit

        val log3 = log(ToJson(Hello("Como estas")).get, "my service class", "My another Category")

        assert(log3.event == ToJson(Hello("Como estas")).get)
        assert(log3.serviceClass == "my service class")
        assert(log3.category == "My another Category")

        assert(log1.event == ToJson(Hello("Hola")).get)
        assert(log1.category == "My Category")

        assert(log2.event == ToJson(Hello("Como estas")).get)
        assert(log2.category == "My another Category")

        consumeFirstStringMessageFrom("com.ubirch.eventlog") mustBe log1.toString

        consumeFirstStringMessageFrom("com.ubirch.eventlog") mustBe log2.toString

        getStringProducer.getProducer.close()
      }

    }

  }

}
