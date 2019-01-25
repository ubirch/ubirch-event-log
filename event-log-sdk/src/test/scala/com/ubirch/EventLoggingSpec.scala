package com.ubirch

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.sdk.EventLogging
import com.ubirch.services.kafka.producer.{ Configs, StringProducer }
import com.ubirch.util.Implicits.configsToProps
import com.ubirch.util.ToJson
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.scalatest.mockito.MockitoSugar

case class Hello(name: String)

class EventLoggingSpec extends TestBase with MockitoSugar with LazyLogging {

  "EventLogging" must {

    "log message" in {

      implicit val kafKaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      withRunningKafka {

        val logger = new EventLogging

        import logger._

        val configs = Configs(bootstrapServers = "localhost:" + kafKaConfig.kafkaPort)

        setStringProducer(new StringProducer(configs))

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

        consumeFirstStringMessageFrom("com.ubirch.eventlog") mustBe log1.toString

        consumeFirstStringMessageFrom("com.ubirch.eventlog") mustBe log2.toString

        getStringProducer.producer.close()
      }

    }

  }

}
