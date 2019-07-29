package com.ubirch

import java.util.Date

import com.google.inject.binder.ScopedBindingBuilder
import com.typesafe.config.{ Config, ConfigValueFactory }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.{ ConsumerConfPaths, ProducerConfPaths }
import com.ubirch.chainer.models.Chainables.eventLogChainable
import com.ubirch.chainer.models.{ Chainer, Master, Mode, Slave }
import com.ubirch.chainer.services.ChainerServiceBinder
import com.ubirch.kafka.consumer.{ All, StringConsumer }
import com.ubirch.models.EnrichedEventLog.enrichedEventLog
import com.ubirch.models._
import com.ubirch.services.config.ConfigProvider
import com.ubirch.util._
import io.prometheus.client.CollectorRegistry
import net.manub.embeddedkafka.EmbeddedKafkaConfig
import org.json4s.JsonAST._

class InjectorHelperImpl(bootstrapServers: String, consumerTopic: String, producerTopic: String, minTreeRecords: Int = 10, treeEvery: Int = 60, mode: Mode = Slave) extends InjectorHelper(List(new ChainerServiceBinder {

  override def config: ScopedBindingBuilder = bind(classOf[Config]).toProvider(new ConfigProvider {
    override def conf: Config = {
      super.conf
        .withValue(
          "eventLog.mode",
          ConfigValueFactory.fromAnyRef(mode.value)
        )
        .withValue(
          "eventLog.minTreeRecords",
          ConfigValueFactory.fromAnyRef(minTreeRecords)
        )
        .withValue(
          "eventLog.treeEvery",
          ConfigValueFactory.fromAnyRef(treeEvery)
        )
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

    "consume, process and publish tree and event logs in Slave mode" in {

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
              .withCategory(Values.UPP_CATEGORY)
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

        val mode = Slave

        assert(treeEventLogAsString.nonEmpty)
        assert(treeEventLog.id.nonEmpty)
        assert(treeEventLog.customerId == mode.customerId)
        assert(treeEventLog.serviceClass == mode.serviceClass)
        assert(treeEventLog.category == mode.category)
        assert(treeEventLog.signature == SigningHelper.signAndGetAsHex(config, SigningHelper.getBytesFromString(node.toString)))
        assert(EventLogJsonSupport.ToJson(chainer.getNode).get == treeEventLog.event)
        assert(treeEventLog.headers == Headers.create(HeaderNames.TRACE -> mode.value, HeaderNames.ORIGIN -> mode.category))
        assert(treeEventLog.id == chainer.getNode.map(_.value).getOrElse("NO_ID"))
        assert(treeEventLog.lookupKeys ==
          Seq(
            LookupKey(
              mode.lookupName,
              mode.category,
              (treeEventLog.id, mode.category),
              chainer.es.map(x => (x.id, x.category))
            )
          ))
        assert(treeEventLog.category == treeEventLog.lookupKeys.headOption.map(_.category).getOrElse("No CAT"))
        assert(events.map(_.id).sorted == chainer.es.map(_.id).sorted)
        assert(events.size == chainer.es.size)
        assert(events.size == treeEventLog.lookupKeys.flatMap(_.value).size)
        assert(maxNumberToRead == messages.size)
        assert(chainer.getNodes.map(_.value).size == customerIds.size)
        assert(chainer.getHashes.flatten.size == events.size)

      }

    }

    "consume, process and publish tree and event logs in Master mode" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val messageEnvelopeTopic = "com.ubirch.messageenvelope"
      val eventLogTopic = "com.ubirch.eventlog"

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort
      val InjectorHelper = new InjectorHelperImpl(bootstrapServers, messageEnvelopeTopic, eventLogTopic, mode = Master)
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
              .withCategory(Values.UPP_CATEGORY)
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

        val mode = Master

        assert(treeEventLogAsString.nonEmpty)
        assert(treeEventLog.id.nonEmpty)
        assert(treeEventLog.customerId == mode.customerId)
        assert(treeEventLog.serviceClass == mode.serviceClass)
        assert(treeEventLog.category == mode.category)
        assert(treeEventLog.signature == SigningHelper.signAndGetAsHex(config, SigningHelper.getBytesFromString(node.toString)))
        assert(EventLogJsonSupport.ToJson(chainer.getNode).get == treeEventLog.event)
        assert(treeEventLog.headers == Headers.create(HeaderNames.TRACE -> mode.value, HeaderNames.ORIGIN -> mode.category))
        assert(treeEventLog.id == chainer.getNode.map(_.value).getOrElse("NO_ID"))
        assert(treeEventLog.lookupKeys ==
          Seq(
            LookupKey(
              mode.lookupName,
              mode.category,
              (treeEventLog.id, mode.category),
              chainer.es.map(x => (x.id, x.category))
            )
          ))
        assert(treeEventLog.category == treeEventLog.lookupKeys.headOption.map(_.category).getOrElse("No CAT"))
        assert(events.map(_.id).sorted == chainer.es.map(_.id).sorted)
        assert(events.size == chainer.es.size)
        assert(events.size == treeEventLog.lookupKeys.flatMap(_.value).size)
        assert(maxNumberToRead == messages.size)
        assert(chainer.getNodes.map(_.value).size == customerIds.size)
        assert(chainer.getHashes.flatten.size == events.size)

      }

    }

    "consume, process and publish tree and event logs after time threshold is reached" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val messageEnvelopeTopic = "com.ubirch.messageenvelope"
      val eventLogTopic = "com.ubirch.eventlog"

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort
      val InjectorHelper = new InjectorHelperImpl(bootstrapServers, messageEnvelopeTopic, eventLogTopic, minTreeRecords = 10, treeEvery = 6)
      val config = InjectorHelper.get[Config]

      withRunningKafka {

        val customerIds = List("Sun")
        val customerRange = 0 to 7

        val events = customerIds.flatMap { x =>
          customerRange.map(_ =>

            EventLog(JString(UUIDHelper.randomUUID.toString))
              .withEventTime(new Date())
              .withRandomNonce
              .withCustomerId(x)
              .withNewId
              .withCategory(Values.UPP_CATEGORY)
              .sign(config))
        }

        events.foreach(x => publishStringMessageToKafka(messageEnvelopeTopic, x.toJson))

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)
        consumer.startPolling()
        //Consumer

        Thread.sleep(10000)

        val maxNumberToRead = 1 /* tree */
        val messages = consumeNumberStringMessagesFrom(eventLogTopic, maxNumberToRead)

        val treeEventLogAsString = messages.headOption.getOrElse("")
        val treeEventLog = EventLogJsonSupport.FromString[EventLog](treeEventLogAsString).get
        val chainer = ChainerSpec.getChainer(events)
        val node = EventLogJsonSupport.ToJson(chainer.getNode).get

        val category = Values.SLAVE_TREE_CATEGORY

        assert(treeEventLogAsString.nonEmpty)
        assert(treeEventLog.id.nonEmpty)
        assert(treeEventLog.customerId == Values.UBIRCH)
        assert(treeEventLog.serviceClass == "ubirchChainerSlave")
        assert(treeEventLog.category == category)
        assert(treeEventLog.signature == SigningHelper.signAndGetAsHex(config, SigningHelper.getBytesFromString(node.toString)))
        assert(EventLogJsonSupport.ToJson(chainer.getNode).get == treeEventLog.event)
        assert(treeEventLog.headers == Headers.create(HeaderNames.TRACE -> Slave.value, HeaderNames.ORIGIN -> category))
        assert(treeEventLog.id == chainer.getNode.map(_.value).getOrElse("NO_ID"))
        assert(treeEventLog.lookupKeys ==
          Seq(
            LookupKey(
              Values.SLAVE_TREE_ID,
              category,
              (treeEventLog.id, category),
              chainer.es.map(x => (x.id, x.category))
            )
          ))
        assert(treeEventLog.category == treeEventLog.lookupKeys.headOption.map(_.category).getOrElse("No CAT"))
        assert(events.map(_.id).sorted == chainer.es.map(_.id).sorted)
        assert(events.size == chainer.es.size)
        assert(events.size == treeEventLog.lookupKeys.flatMap(_.value).size)
        assert(maxNumberToRead == messages.size)
        assert(chainer.getNodes.map(_.value).size == customerIds.size)
        assert(chainer.getHashes.flatten.size == events.size)

      }

    }

    "consume, process and publish tree and event logs after records threshold is reached" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val messageEnvelopeTopic = "com.ubirch.messageenvelope"
      val eventLogTopic = "com.ubirch.eventlog"

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort
      val InjectorHelper = new InjectorHelperImpl(bootstrapServers, messageEnvelopeTopic, eventLogTopic, minTreeRecords = 10)
      val config = InjectorHelper.get[Config]

      withRunningKafka {

        val customerIds = List("Sun")
        val customerRange = 0 to 11

        val events = customerIds.flatMap { x =>
          customerRange.map(_ =>

            EventLog(JString(UUIDHelper.randomUUID.toString))
              .withEventTime(new Date())
              .withRandomNonce
              .withCustomerId(x)
              .withNewId
              .withCategory(Values.UPP_CATEGORY)
              .sign(config))
        }

        val (e1s, e2s) = events.splitAt(7)

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)
        consumer.startPolling()
        //Consumer

        e1s.foreach(x => publishStringMessageToKafka(messageEnvelopeTopic, x.toJson))

        Thread.sleep(5000)

        val numberOfPauses = consumer.getPausedHistory

        assert(numberOfPauses.get() > 0)

        e2s.foreach(x => publishStringMessageToKafka(messageEnvelopeTopic, x.toJson))

        Thread.sleep(7000)

        val maxNumberToRead = 1 /* tree */
        val messages = consumeNumberStringMessagesFrom(eventLogTopic, maxNumberToRead)

        val treeEventLogAsString = messages.headOption.getOrElse("")
        val treeEventLog = EventLogJsonSupport.FromString[EventLog](treeEventLogAsString).get
        val chainer = ChainerSpec.getChainer(events)
        val node = EventLogJsonSupport.ToJson(chainer.getNode).get

        val category = Values.SLAVE_TREE_CATEGORY

        assert(treeEventLogAsString.nonEmpty)
        assert(treeEventLog.id.nonEmpty)
        assert(treeEventLog.customerId == Values.UBIRCH)
        assert(treeEventLog.serviceClass == "ubirchChainerSlave")
        assert(treeEventLog.category == category)
        assert(treeEventLog.signature == SigningHelper.signAndGetAsHex(config, SigningHelper.getBytesFromString(node.toString)))
        assert(EventLogJsonSupport.ToJson(chainer.getNode).get == treeEventLog.event)
        assert(treeEventLog.headers == Headers.create(HeaderNames.TRACE -> Slave.value, HeaderNames.ORIGIN -> category))
        assert(treeEventLog.id == chainer.getNode.map(_.value).getOrElse("NO_ID"))
        assert(treeEventLog.lookupKeys ==
          Seq(
            LookupKey(
              Values.SLAVE_TREE_ID,
              category,
              (treeEventLog.id, category),
              chainer.es.map(x => (x.id, x.category))
            )
          ))
        assert(treeEventLog.category == treeEventLog.lookupKeys.headOption.map(_.category).getOrElse("No CAT"))
        assert(events.map(_.id).sorted == chainer.es.map(_.id).sorted)
        assert(events.size == chainer.es.size)
        assert(events.size == treeEventLog.lookupKeys.flatMap(_.value).size)
        assert(maxNumberToRead == messages.size)
        assert(chainer.getNodes.map(_.value).size == customerIds.size)
        assert(chainer.getHashes.flatten.size == events.size)

      }

    }

    "consume, process and publish tree and event logs after records threshold is reached in Master mode" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val messageEnvelopeTopic = "com.ubirch.messageenvelope"
      val eventLogTopic = "com.ubirch.eventlog"

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort
      val InjectorHelper = new InjectorHelperImpl(bootstrapServers, messageEnvelopeTopic, eventLogTopic, minTreeRecords = 11, mode = Master)
      val config = InjectorHelper.get[Config]

      withRunningKafka {

        val customerIds = List("Sun")
        val customerRange = 0 to 11

        val events = customerIds.flatMap { x =>
          customerRange.map(_ =>

            EventLog(JString(UUIDHelper.randomUUID.toString))
              .withEventTime(new Date())
              .withRandomNonce
              .withCustomerId(x)
              .withNewId
              .withCategory(Values.UPP_CATEGORY)
              .sign(config))
        }

        val (e1s, e2s) = events.splitAt(7)

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)
        consumer.startPolling()
        //Consumer

        e1s.foreach(x => publishStringMessageToKafka(messageEnvelopeTopic, x.toJson))

        Thread.sleep(7000)

        val numberOfPauses = consumer.getPausedHistory

        assert(numberOfPauses.get() > 0)

        e2s.foreach(x => publishStringMessageToKafka(messageEnvelopeTopic, x.toJson))

        Thread.sleep(7000)

        val maxNumberToRead = 1 /* tree */
        val messages = consumeNumberStringMessagesFrom(eventLogTopic, maxNumberToRead)

        val treeEventLogAsString = messages.headOption.getOrElse("")
        val treeEventLog = EventLogJsonSupport.FromString[EventLog](treeEventLogAsString).get
        val chainer = ChainerSpec.getChainer(events)
        val node = EventLogJsonSupport.ToJson(chainer.getNode).get

        val mode = Master

        assert(treeEventLogAsString.nonEmpty)
        assert(treeEventLog.id.nonEmpty)
        assert(treeEventLog.customerId == mode.customerId)
        assert(treeEventLog.serviceClass == mode.serviceClass)
        assert(treeEventLog.category == mode.category)
        assert(treeEventLog.signature == SigningHelper.signAndGetAsHex(config, SigningHelper.getBytesFromString(node.toString)))
        assert(EventLogJsonSupport.ToJson(chainer.getNode).get == treeEventLog.event)
        assert(treeEventLog.headers == Headers.create(HeaderNames.TRACE -> mode.value, HeaderNames.ORIGIN -> mode.category))
        assert(treeEventLog.id == chainer.getNode.map(_.value).getOrElse("NO_ID"))
        assert(treeEventLog.lookupKeys ==
          Seq(LookupKey(
            mode.lookupName,
            mode.category,
            (treeEventLog.id, mode.category),
            chainer.es.map(x => (x.id, x.category))
          )))
        assert(treeEventLog.category == treeEventLog.lookupKeys.headOption.map(_.category).getOrElse("No CAT"))
        assert(events.map(_.id).sorted == chainer.es.map(_.id).sorted)
        assert(events.size == chainer.es.size)
        assert(events.size == treeEventLog.lookupKeys.flatMap(_.value).size)
        assert(maxNumberToRead == messages.size)
        assert(chainer.getNodes.map(_.value).size == customerIds.size)
        assert(chainer.getHashes.flatten.size == events.size)

      }

    }

  }

  override protected def beforeEach(): Unit = {
    CollectorRegistry.defaultRegistry.clear()
  }

}
