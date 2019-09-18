package com.ubirch.dispatcher

import java.util.concurrent.TimeoutException

import com.google.inject.binder.ScopedBindingBuilder
import com.typesafe.config.{Config, ConfigValueFactory}
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.dispatcher.services.{DispatchInfo, DispatcherServiceBinder}
import com.ubirch.kafka.consumer.{All, StringConsumer}
import com.ubirch.models.{EventLog, HeaderNames, Values}
import com.ubirch.services.config.ConfigProvider
import com.ubirch.util._
import io.prometheus.client.CollectorRegistry
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.json4s.JsonAST.JString

class InjectorHelperImpl(bootstrapServers: String) extends InjectorHelper(List(new DispatcherServiceBinder {
  override def config: ScopedBindingBuilder = bind(classOf[Config]).toProvider(new ConfigProvider {
    override def conf: Config = {
      super.conf
        .withValue(
          "eventLog.kafkaConsumer.bootstrapServers",
          ConfigValueFactory.fromAnyRef(bootstrapServers)
        )
        .withValue(
          "eventLog.kafkaProducer.bootstrapServers",
          ConfigValueFactory.fromAnyRef(bootstrapServers)
        )
    }
  })
}))

class DispatchExecutorSpec extends TestBase with LazyLogging {

  "Dispatch Spec" must {

    "consume and dispatch successfully 3000" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val messageEnvelopeTopic = "com.ubirch.eventlog.dispatch_request"

        val dispatchInfo = InjectorHelper.get[DispatchInfo].info

        val maybeDispatch = dispatchInfo.find(d => d.category == Values.UPP_CATEGORY)

        val range = (1 to 3000)
        val eventLogs = range.map { _ =>
          EventLog(JString(UUIDHelper.randomUUID.toString)).withCategory(Values.UPP_CATEGORY).withNewId
        }

        eventLogs.foreach { x =>
          publishStringMessageToKafka(messageEnvelopeTopic, x.toJson)
        }

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)

        consumer.startPolling()
        //Consumer

        Thread.sleep(20000)

        var total = 0

        maybeDispatch match {
          case Some(s) =>
            s.topics.map { t =>
              val fromTopic = consumeNumberStringMessagesFrom(t.name, range.size)
              total = total + fromTopic.size
              assert(range.size == fromTopic.size)
            }
          case None =>
            assert(1 != 1)
        }

        assert(total == range.size * maybeDispatch.map(_.topics.size).getOrElse(0))

      }

    }

    "consume and dispatch successfully" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val messageEnvelopeTopic = "com.ubirch.eventlog.dispatch_request"

        val dispatchInfo = InjectorHelper.get[DispatchInfo].info

        val eventLogs = dispatchInfo.map { x =>
          EventLog(JString(UUIDHelper.randomUUID.toString)).withCategory(x.category).withNewId
        }

        eventLogs.foreach { x =>
          publishStringMessageToKafka(messageEnvelopeTopic, x.toJson)
        }

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        dispatchInfo.map { x =>

          val topicSize = x.topics.size
          var topicsProcessed = 0

          x.topics.map { t =>
            topicsProcessed = topicsProcessed + 1
            val readMessage = consumeFirstStringMessageFrom(t.name)
            t.dataToSend.filter(_.isEmpty).map { _ =>
              val dispatchRes = EventLogJsonSupport.FromString[EventLog](readMessage).get
              assert(eventLogs.contains(dispatchRes))
              assert(eventLogs.map(_.category).contains(dispatchRes.category))
            }

          }

          assert(topicSize == topicsProcessed)

        }

      }

    }

    "consume and dispatch successfully with tag-exclude headers" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort

      val InjectorHelper = new InjectorHelperImpl(bootstrapServers)

      withRunningKafka {

        val messageEnvelopeTopic = "com.ubirch.eventlog.dispatch_request"

        val dispatchInfo = InjectorHelper.get[DispatchInfo].info

        val eventLogs = dispatchInfo.map { x =>
          EventLog(JString(UUIDHelper.randomUUID.toString))
            .withCategory(x.category)
            .withNewId
            .addHeaders(HeaderNames.DISPATCHER -> "tags-exclude:aggregation")
        }

        eventLogs.foreach { x =>
          publishStringMessageToKafka(messageEnvelopeTopic, x.toJson)
        }

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val topicSize = dispatchInfo.flatMap(_.topics).size
        var topicsProcessed = 0

        dispatchInfo.map { x =>

          x.topics.map { dispatchTopic =>

            if (dispatchTopic.tags.contains("aggregation")) {
              assertThrows[TimeoutException](consumeFirstStringMessageFrom(dispatchTopic.name))
            } else {

              topicsProcessed = topicsProcessed + 1
              dispatchTopic.dataToSend.filter(_.isEmpty).map { _ =>
                val dispatchRes = EventLogJsonSupport.FromString[EventLog](consumeFirstStringMessageFrom(dispatchTopic.name)).get
                assert(eventLogs.contains(dispatchRes))
                assert(eventLogs.map(_.category).contains(dispatchRes.category))
              }
            }
          }

        }

        assert(topicSize > topicsProcessed && topicsProcessed > 0)


      }

    }
  }

  override protected def beforeEach(): Unit = {
    CollectorRegistry.defaultRegistry.clear()
  }

}
