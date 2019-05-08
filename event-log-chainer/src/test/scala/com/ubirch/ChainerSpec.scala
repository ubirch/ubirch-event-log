package com.ubirch

import java.util.Date

import com.google.inject.binder.ScopedBindingBuilder
import com.typesafe.config.{ Config, ConfigValueFactory }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.{ ConsumerConfPaths, ProducerConfPaths }
import com.ubirch.chainer.ServiceTraits
import com.ubirch.chainer.models.Chainables.eventLogChainable
import com.ubirch.chainer.models.Chainer
import com.ubirch.chainer.services.ChainerServiceBinder
import com.ubirch.kafka.consumer.{ All, StringConsumer }
import com.ubirch.models.EnrichedEventLog.enrichedEventLog
import com.ubirch.models.{ EventLog, HeaderNames, Headers, LookupKey }
import com.ubirch.services.config.ConfigProvider
import com.ubirch.util._
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.json4s.JsonAST._

class InjectorHelperImpl(bootstrapServers: String, consumerTopic: String, producerTopic: String) extends InjectorHelper(List(new ChainerServiceBinder {

  object ConsumerConfPaths extends ConsumerConfPaths
  object ProducerConfPaths extends ProducerConfPaths

  override def config: ScopedBindingBuilder = bind(classOf[Config]).toProvider(new ConfigProvider {
    override def conf: Config = {
      super.conf
        .withValue(
          ConsumerConfPaths.BOOTSTRAP_SERVERS,
          ConfigValueFactory.fromAnyRef(bootstrapServers)
        )
        .withValue(
          ProducerConfPaths.BOOTSTRAP_SERVERS,
          ConfigValueFactory.fromAnyRef(bootstrapServers)
        ).withValue(
            ConsumerConfPaths.TOPIC_PATH,
            ConfigValueFactory.fromAnyRef(consumerTopic)
          )
        .withValue(
          ProducerConfPaths.TOPIC_PATH,
          ConfigValueFactory.fromAnyRef(producerTopic)
        )
    }
  })
}))

object ChainerSpec {

  def getChainer(events: List[EventLog]): Chainer[EventLog] = {
    new Chainer(events)
      .createGroups
      .createSeedHashes
      .createSeedNodes(keepOrder = true)
      .createNode
  }

}

class ChainerSpec extends TestBase with LazyLogging {

  "Chainer Spec" must {

    "consume, process and publish tree and event logs" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val messageEnvelopeTopic = "com.ubirch.messageenvelope"
      val eventLogTopic = "com.ubirch.eventlog"

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort
      val InjectorHelper = new InjectorHelperImpl(bootstrapServers, messageEnvelopeTopic, eventLogTopic)
      val config = InjectorHelper.get[Config]

      withRunningKafka {

        val customerIds = List("Sun", "Earth", "Marz")
        val customerRange = 0 to 3

        val events = customerIds.flatMap { x =>
          customerRange.map(_ =>

            EventLog(JString(UUIDHelper.randomUUID.toString))
              .withEventTime(new Date())
              .withRandomNonce
              .withCustomerId(x)
              .withNewId
              .withCategory(ServiceTraits.ADAPTER_CATEGORY)
              .sign(config))
        }

        events.foreach(x => publishStringMessageToKafka(messageEnvelopeTopic, x.toJson))

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)
        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        val maxNumberToRead = 1 /* tree */
        val messages = consumeNumberStringMessagesFrom(eventLogTopic, maxNumberToRead)

        val treeEventLogAsString = messages.headOption.getOrElse("")
        val treeEventLog = EventLogJsonSupport.FromString[EventLog](treeEventLogAsString).get
        val chainer = ChainerSpec.getChainer(events)
        val node = EventLogJsonSupport.ToJson(chainer.getNode).get

        assert(treeEventLogAsString.nonEmpty)
        assert(treeEventLog.id.nonEmpty)
        assert(treeEventLog.customerId == "ubirch")
        assert(treeEventLog.serviceClass == "ubirchChainerSlave")
        assert(treeEventLog.category == ServiceTraits.SLAVE_TREE_CATEGORY)
        assert(treeEventLog.signature == SigningHelper.signAndGetAsHex(config, SigningHelper.getBytesFromString(node.toString)))
        assert(EventLogJsonSupport.ToJson(chainer.getNode).get == treeEventLog.event)
        assert(treeEventLog.headers == Headers.create(HeaderNames.ORIGIN -> ServiceTraits.SLAVE_TREE_CATEGORY))
        assert(treeEventLog.id == chainer.getNode.map(_.value).getOrElse(""))
        assert(treeEventLog.lookupKeys == Seq(LookupKey(LookupKey.SLAVE_TREE_ID, LookupKey.SLAVE_TREE, treeEventLog.id, chainer.getNodes.map(_.value))))
        assert(maxNumberToRead == messages.size)
        assert(chainer.getNodes.map(_.value).size == customerIds.size)
        assert(chainer.getHashes.flatten.size == events.size)

      }

    }
  }

}
