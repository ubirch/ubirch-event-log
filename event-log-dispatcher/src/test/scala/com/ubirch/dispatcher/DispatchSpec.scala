package com.ubirch.dispatcher

import com.google.inject.binder.ScopedBindingBuilder
import com.typesafe.config.{ Config, ConfigValueFactory }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.dispatcher.services.{ DispatchInfo, DispatcherServiceBinder }
import com.ubirch.kafka.consumer.StringConsumer
import com.ubirch.models.EventLog
import com.ubirch.services.config.ConfigProvider
import com.ubirch.util._
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

class DispatchSpec extends TestBase with LazyLogging {

  "Dispatch Spec" must {

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

        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        dispatchInfo.map { x =>

          x.topics.map { t =>
            val readMessage = consumeFirstStringMessageFrom(t.name)
            t.dataToSend.filter(_.isEmpty).map { _ =>
              val dispatchRes = EventLogJsonSupport.FromString[EventLog](readMessage).get
              assert(eventLogs.contains(dispatchRes))
              assert(eventLogs.map(_.category).contains(dispatchRes.category))
            }

          }

        }

      }

    }
  }

}
