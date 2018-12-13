package com.ubirch.services.kafka

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.Events
import com.ubirch.services.lifeCycle.DefaultLifecycle
import net.manub.embeddedkafka.{ EmbeddedKafka, EmbeddedKafkaConfig }
import org.scalatest.mockito.MockitoSugar
import org.mockito.Mockito._

import scala.concurrent.ExecutionContext.Implicits.global

class StringConsumerSpec extends TestBase with MockitoSugar with LazyLogging {

  "StringConsumerSpec" must {

    "should respond to simple publish/subscribe test" in {

      implicit val config = EmbeddedKafkaConfig(kafkaPort = 9092)

      withRunningKafka {

        publishStringMessageToKafka("test", Entities.Events.eventExampleAsString)

        val lifeCycle = mock[DefaultLifecycle]
        val events = mock[Events]

        val executor = new DefaultExecutor(
          DefaultExecutorFamily(
            new Wrapper,
            new FilterEmpty,
            new StringLogger,
            new EventLogLogger,
            new EventLogParser,
            new EventsStore(events)), events)

        val consumer = new DefaultStringConsumerUnit(
          ConfigFactory.load(),
          lifeCycle,
          events,
          executor)

        consumer.get().startPolling()

        Thread.sleep(5000)

      }

    }

  }

}