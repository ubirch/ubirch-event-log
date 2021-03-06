package com.ubirch

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.kafka.producer.{ Configs, StringProducer }
import com.ubirch.kafka.util.PortGiver
import com.ubirch.sdk.EventLogging
import com.ubirch.util.EventLogJsonSupport
import net.manub.embeddedkafka.EmbeddedKafkaConfig
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

        val topic = "com.ubirch.eventlog.dispatch_request"

        setStringProducer(StringProducer(configs))

        val logged = logger.log(Hello("Hello")).withCustomerId("my customer id").commit

        consumeFirstStringMessageFrom(topic) mustBe logged.toJson

        val logged2 = logger.log(EventLogJsonSupport.ToJson("Hola").get).withCustomerId("my customer id").commit

        consumeFirstStringMessageFrom(topic) mustBe logged2.toJson

        val log1 = log(EventLogJsonSupport.ToJson(Hello("Hola")).get, "My Category").withCustomerId("my customer id")

        val log2 = log(EventLogJsonSupport.ToJson(Hello("Como estas")).get, "My another Category").withCustomerId("my customer id 2")

        //Let's unite them in order first in first out
        val log1_2 = log1 +> log2

        //Let's actually commit it
        log1_2.commit

        val log3 = log(
          EventLogJsonSupport.ToJson(Hello("Como estas")).get,
          "my service class",
          "My another Category"
        ).withCustomerId("my customer id")

        assert(log3.event == EventLogJsonSupport.ToJson(Hello("Como estas")).get)
        assert(log3.serviceClass == "my service class")
        assert(log3.category == "My another Category")

        assert(log1.event == EventLogJsonSupport.ToJson(Hello("Hola")).get)
        assert(log1.category == "My Category")
        assert(log1.customerId == "my customer id")

        assert(log2.event == EventLogJsonSupport.ToJson(Hello("Como estas")).get)
        assert(log2.category == "My another Category")
        assert(log2.customerId == "my customer id 2")

        consumeFirstStringMessageFrom(topic) mustBe log1.toJson

        consumeFirstStringMessageFrom(topic) mustBe log2.toJson

        getStringProducer.getProducer.close()
      }

    }

  }

}
