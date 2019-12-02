package com.ubirch

import java.io.ByteArrayInputStream
import java.util.Date
import java.util.concurrent.{Executor, TimeoutException}

import com.google.inject.Provider
import com.google.inject.binder.ScopedBindingBuilder
import com.typesafe.config.{Config, ConfigValueFactory}
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.{ConsumerConfPaths, ProducerConfPaths}
import com.ubirch.chainer.models.Chainables.eventLogChainable
import com.ubirch.chainer.models._
import com.ubirch.chainer.services.ChainerServiceBinder
import com.ubirch.chainer.services.httpClient.{WebClient, WebclientResponse}
import com.ubirch.chainer.services.tree.TreeMonitor
import com.ubirch.chainer.util.{ChainerJsonSupport, PMHelper}
import com.ubirch.kafka.consumer.{All, StringConsumer}
import com.ubirch.models.EnrichedEventLog.enrichedEventLog
import com.ubirch.models._
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.services.config.ConfigProvider
import com.ubirch.util._
import io.prometheus.client.CollectorRegistry
import net.manub.embeddedkafka.{EmbeddedKafkaConfig, KafkaUnavailableException}
import org.asynchttpclient.Param
import org.json4s.JsonAST._
import org.scalatest.Tag

import scala.annotation.tailrec
import scala.concurrent.Future
import scala.util.control.TailCalls.TailRec

class WebClientProvider extends Provider[WebClient] {
  override def get(): WebClient = new WebClient {
    override def get(url: String)(params: List[Param])(implicit exec: Executor): Future[WebclientResponse] = {
      url match {
        case "http://localhost:8081/v1/events" =>
          val body = """{"success":true,"message":"Nothing Found","data":[]}"""
          Future.successful(WebclientResponse("OK", 200, "application/json", body, new ByteArrayInputStream(body.getBytes())))
      }
    }
  }
}

class InjectorHelperImpl(
    bootstrapServers: String,
    consumerTopic: String,
    producerTopic: String,
    minTreeRecords: Int = 10,
    treeEvery: Int = 60,
    treeUpgrade: Int = 120,
    mode: Mode = Slave,
    split: Boolean = false,
    webClientProvider: Option[Provider[WebClient]] = None
) extends InjectorHelper(List(new ChainerServiceBinder {

  override def config: ScopedBindingBuilder = bind(classOf[Config]).toProvider(new ConfigProvider {
    override def conf: Config = {
      super.conf
        .withValue(
          "eventLog.daysBack",
          ConfigValueFactory.fromAnyRef(3)
        )
        .withValue(
          "eventLog.split",
          ConfigValueFactory.fromAnyRef(split)
        )
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
          "eventLog.treeUpgrade",
          ConfigValueFactory.fromAnyRef(treeUpgrade)
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

  override def webClient: ScopedBindingBuilder = webClientProvider.map(x => bind(classOf[WebClient]).toProvider(x)).getOrElse(super.webClient)
}))

object ChainerSpec {

  def getChainer(events: List[EventLog]): Chainer[EventLog] = {
    new Chainer(events)
      .withGeneralGrouping
      .createSeedHashes
      .createSeedNodes(keepOrder = true)
      .createNode
  }

  def getChainerWithGeneral(events: List[EventLog]): Chainer[EventLog] = {
    new Chainer(events)
      .withGeneralGrouping
      .createSeedHashes
      .createSeedNodes(keepOrder = true)
      .createNode
  }

}

class ChainerSpec extends TestBase with LazyLogging {

  import LookupKey._

  val messageEnvelopeTopic = "com.ubirch.messageenvelope"
  val eventLogTopic = "com.ubirch.eventlog"

  def readMessage(topic: String, onStartWait: Int = 5000, maxRetries: Int = 10, maxToRead: Int = 1, sleepInBetween: Int = 500)(implicit kafkaConfig: EmbeddedKafkaConfig): List[String] =  {
    @tailrec
    def go(acc: Int): List[String] = {
      try {
        logger.info("Trying to get value.")
        consumeNumberStringMessagesFrom(topic, maxToRead)
      } catch {
        case e: KafkaUnavailableException =>
          throw e
        case e: TimeoutException =>
          logger.warn("Starting retry")
          if(acc == 0){
            throw e
          } else {
            Thread.sleep(sleepInBetween)
            go(acc - 1)
          }
      }
    }

    if(onStartWait > 0){
      Thread.sleep(onStartWait)
    }

    go(maxRetries)
  }

  "Chainer Spec" must {

    "consume, process and publish tree and event logs in Slave mode" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort
      val InjectorHelper = new InjectorHelperImpl(bootstrapServers, messageEnvelopeTopic, eventLogTopic)
      val config = InjectorHelper.get[Config]

      val customerIds = List("Sun", "Earth", "Marz")
      val customerRange = 0 to 3

      val events = customerIds.flatMap { x =>
        customerRange.map(_ =>

          EventLog(ChainerJsonSupport.ToJson[ProtocolMessage](PMHelper.createPM).get)
            .withEventTime(new Date())
            .withRandomNonce
            .withCustomerId(x)
            .withNewId
            .withCategory(Values.UPP_CATEGORY)
            .sign(config))
      }

      withRunningKafka {

        events.foreach(x => publishStringMessageToKafka(messageEnvelopeTopic, x.toJson))

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)
        consumer.startPolling()
        //Consumer

        val maxNumberToRead = 1 /* tree */

        val messages: List[String] = readMessage(eventLogTopic +1)

        logger.info("Messages Read:" + messages)

        val treeEventLogAsString = messages.headOption.getOrElse("")
        val treeEventLog = ChainerJsonSupport.FromString[EventLog](treeEventLogAsString).get
        val chainer = ChainerSpec.getChainer(events)
        val node = Chainer
          .compress(chainer)
          .map(x => ChainerJsonSupport.ToJson(x).get)
          .getOrElse(JString("WHAT"))

        val mode = Slave

        lazy val valuesStrategy = ValueStrategy.getStrategyForNormalLeaves(mode)

        assert(treeEventLogAsString.nonEmpty)
        assert(treeEventLog.id.nonEmpty)
        assert(treeEventLog.customerId == mode.customerId)
        assert(treeEventLog.serviceClass == mode.serviceClass)
        assert(treeEventLog.category == mode.category)
        assert(treeEventLog.signature == SigningHelper.signAndGetAsHex(config, SigningHelper.getBytesFromString(node.toString)))
        assert(treeEventLog.event == node)
        assert(treeEventLog.headers == Headers.create(
          HeaderNames.TRACE -> mode.value,
          HeaderNames.ORIGIN -> mode.category,
          TreeMonitor.headersNormalCreationFromMode(mode)
        ))
        assert(treeEventLog.id == chainer.getNode.map(_.value).getOrElse("NO_ID"))
        assert(treeEventLog.lookupKeys ==
          Seq(
            LookupKey(
              mode.lookupName,
              mode.category,
              treeEventLog.id.asKeyWithLabel(mode.category),
              chainer.seeds.flatMap(x => valuesStrategy.create(x))
            )
          ))
        assert(treeEventLog.category == treeEventLog.lookupKeys.headOption.map(_.category).getOrElse("No CAT"))
        assert(events.map(_.id).sorted == chainer.seeds.map(_.id).sorted)
        assert(events.size == chainer.seeds.size)
        assert(events.size == treeEventLog.lookupKeys.flatMap(_.value).size)
        assert(maxNumberToRead == messages.size)
        //assert(chainer.getNodes.map(_.value).size == customerIds.size) for when there's explicit grouping
        assert(chainer.getNodes.map(_.value).size == 1)
        assert(chainer.getHashes.flatten.size == events.size)

      }

    }

    "consume, process and publish tree and event logs in Master mode" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

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
              .withCategory(Values.SLAVE_TREE_CATEGORY)
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
        val treeEventLog = ChainerJsonSupport.FromString[EventLog](treeEventLogAsString).get
        val chainer = ChainerSpec.getChainer(events)
        val node = Chainer
          .compress(chainer)
          .map(x => ChainerJsonSupport.ToJson(x).get)
          .getOrElse(JString("WHAT"))

        val mode = Master

        assert(treeEventLogAsString.nonEmpty)
        assert(treeEventLog.id.nonEmpty)
        assert(treeEventLog.customerId == mode.customerId)
        assert(treeEventLog.serviceClass == mode.serviceClass)
        assert(treeEventLog.category == mode.category)
        assert(treeEventLog.signature == SigningHelper.signAndGetAsHex(config, SigningHelper.getBytesFromString(node.toString)))
        assert(treeEventLog.event == node)
        assert(treeEventLog.headers == Headers.create(
          HeaderNames.TRACE -> mode.value,
          HeaderNames.ORIGIN -> mode.category,
          TreeMonitor.headersNormalCreationFromMode(mode)
        ))
        assert(treeEventLog.id == chainer.getNode.map(_.value).getOrElse("NO_ID"))
        assert(treeEventLog.lookupKeys ==
          Seq(
            LookupKey(
              mode.lookupName,
              mode.category,
              treeEventLog.id.asKeyWithLabel(mode.category),
              chainer.seeds.map(x => x.id.asValueWithLabel(x.category))
            )
          ))
        assert(treeEventLog.category == treeEventLog.lookupKeys.headOption.map(_.category).getOrElse("No CAT"))
        assert(events.map(_.id).sorted == chainer.seeds.map(_.id).sorted)
        assert(events.size == chainer.seeds.size)
        assert(events.size == treeEventLog.lookupKeys.flatMap(_.value).size)
        assert(maxNumberToRead == messages.size)
        // assert(chainer.getNodes.map(_.value).size == customerIds.size) Good for when there's explicit grouping
        assert(chainer.getNodes.map(_.value).size == 1)
        assert(chainer.getHashes.flatten.size == events.size)

      }

    }

    "consume, process and publish tree and event logs after time threshold is reached" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort
      val InjectorHelper = new InjectorHelperImpl(bootstrapServers, messageEnvelopeTopic, eventLogTopic, minTreeRecords = 10, treeEvery = 6)
      val config = InjectorHelper.get[Config]

      withRunningKafka {

        val customerIds = List("Sun")
        val customerRange = 0 to 7

        val events = customerIds.flatMap { x =>
          customerRange.map(_ =>

            EventLog(ChainerJsonSupport.ToJson[ProtocolMessage](PMHelper.createPM).get)
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
        val treeEventLog = ChainerJsonSupport.FromString[EventLog](treeEventLogAsString).get
        val chainer = ChainerSpec.getChainer(events)
        val node = Chainer
          .compress(chainer)
          .map(x => ChainerJsonSupport.ToJson(x).get)
          .getOrElse(JString("WHAT"))

        val category = Values.SLAVE_TREE_CATEGORY

        lazy val valuesStrategy = ValueStrategy.getStrategyForNormalLeaves(Slave)

        assert(treeEventLogAsString.nonEmpty)
        assert(treeEventLog.id.nonEmpty)
        assert(treeEventLog.customerId == Values.UBIRCH)
        assert(treeEventLog.serviceClass == "ubirchChainerSlave")
        assert(treeEventLog.category == category)
        assert(treeEventLog.signature == SigningHelper.signAndGetAsHex(config, SigningHelper.getBytesFromString(node.toString)))
        assert(treeEventLog.event == node)
        assert(treeEventLog.headers == Headers.create(
          HeaderNames.TRACE -> Slave.value,
          HeaderNames.ORIGIN -> category,
          TreeMonitor.headersNormalCreationFromMode(Slave)
        ))
        assert(treeEventLog.id == chainer.getNode.map(_.value).getOrElse("NO_ID"))
        assert(treeEventLog.lookupKeys ==
          Seq(
            LookupKey(
              Values.SLAVE_TREE_ID,
              category,
              treeEventLog.id.asKeyWithLabel(category),
              chainer.seeds.flatMap(x => valuesStrategy.create(x))
            )
          ))
        assert(treeEventLog.category == treeEventLog.lookupKeys.headOption.map(_.category).getOrElse("No CAT"))
        assert(events.map(_.id).sorted == chainer.seeds.map(_.id).sorted)
        assert(events.size == chainer.seeds.size)
        assert(events.size == treeEventLog.lookupKeys.flatMap(_.value).size)
        assert(maxNumberToRead == messages.size)
        assert(chainer.getNodes.map(_.value).size == customerIds.size)
        assert(chainer.getHashes.flatten.size == events.size)

      }

    }

    "consume, process and publish tree and event logs after records threshold is reached" taggedAs (Tag("problem")) in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort
      val InjectorHelper = new InjectorHelperImpl(bootstrapServers, messageEnvelopeTopic, eventLogTopic, minTreeRecords = 10)
      val config = InjectorHelper.get[Config]

      val customerIds = List("Sun")
      val customerRange = 0 to 11

      val events = customerIds.flatMap { x =>
        customerRange.map(_ =>

          EventLog(ChainerJsonSupport.ToJson[ProtocolMessage](PMHelper.createPM).get)
            .withEventTime(new Date())
            .withRandomNonce
            .withCustomerId(x)
            .withNewId
            .withCategory(Values.UPP_CATEGORY)
            .sign(config))
      }

      val (e1s, e2s) = events.splitAt(7)

      withRunningKafka {

        //Consumer
        val consumer = InjectorHelper.get[StringConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)
        consumer.startPolling()
        //Consumer

        Thread.sleep(5000)

        e1s.foreach(x => publishStringMessageToKafka(messageEnvelopeTopic, x.toJson))

        Thread.sleep(5000)

        val numberOfPauses = consumer.getPausedHistory

        assert(numberOfPauses.get() > 0)

        e2s.foreach(x => publishStringMessageToKafka(messageEnvelopeTopic, x.toJson))

        Thread.sleep(5000)

        val maxNumberToRead = 1 /* tree */
        val messages = consumeNumberStringMessagesFrom(eventLogTopic, maxNumberToRead)

        val treeEventLogAsString = messages.headOption.getOrElse("")
        val treeEventLog = ChainerJsonSupport.FromString[EventLog](treeEventLogAsString).get
        val chainer = ChainerSpec.getChainer(events)
        val node = Chainer
          .compress(chainer)
          .map(x => ChainerJsonSupport.ToJson(x).get)
          .getOrElse(JString("WHAT"))

        val category = Values.SLAVE_TREE_CATEGORY

        lazy val valuesStrategy = ValueStrategy.getStrategyForNormalLeaves(Slave)

        assert(treeEventLogAsString.nonEmpty)
        assert(treeEventLog.id.nonEmpty)
        assert(treeEventLog.customerId == Values.UBIRCH)
        assert(treeEventLog.serviceClass == "ubirchChainerSlave")
        assert(treeEventLog.category == category)
        assert(treeEventLog.signature == SigningHelper.signAndGetAsHex(config, SigningHelper.getBytesFromString(node.toString)))
        assert(treeEventLog.event == node)
        assert(treeEventLog.headers == Headers.create(
          HeaderNames.TRACE -> Slave.value,
          HeaderNames.ORIGIN -> category,
          TreeMonitor.headersNormalCreationFromMode(Slave)
        ))
        assert(treeEventLog.id == chainer.getNode.map(_.value).getOrElse("NO_ID"))
        assert(treeEventLog.lookupKeys ==
          Seq(
            LookupKey(
              Values.SLAVE_TREE_ID,
              category,
              treeEventLog.id.asKeyWithLabel(category),
              chainer.seeds.flatMap(x => valuesStrategy.create(x))
            )
          ))
        assert(treeEventLog.category == treeEventLog.lookupKeys.headOption.map(_.category).getOrElse("No CAT"))
        assert(events.map(_.id).sorted == chainer.seeds.map(_.id).sorted)
        assert(events.size == chainer.seeds.size)
        assert(events.size == treeEventLog.lookupKeys.flatMap(_.value).size)
        assert(maxNumberToRead == messages.size)
        assert(chainer.getNodes.map(_.value).size == customerIds.size)
        assert(chainer.getHashes.flatten.size == events.size)

      }

    }

    "consume, process and publish tree and event logs after records threshold is reached in Master mode" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort
      val InjectorHelper = new InjectorHelperImpl(bootstrapServers, messageEnvelopeTopic, eventLogTopic, minTreeRecords = 11, mode = Master)
      val config = InjectorHelper.get[Config]

      val customerIds = List("Sun")
      val customerRange = 0 to 11

      val events = customerIds.flatMap { x =>
        customerRange.map(_ =>

          EventLog(JString(UUIDHelper.randomUUID.toString))
            .withEventTime(new Date())
            .withRandomNonce
            .withCustomerId(x)
            .withNewId
            .withCategory(Values.SLAVE_TREE_CATEGORY)
            .sign(config))
      }

      val (e1s, e2s) = events.splitAt(7)

      withRunningKafka {

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
        val treeEventLog = ChainerJsonSupport.FromString[EventLog](treeEventLogAsString).get
        val chainer = ChainerSpec.getChainer(events)
        val node = Chainer
          .compress(chainer)
          .map(x => ChainerJsonSupport.ToJson(x).get)
          .getOrElse(JString("WHAT"))

        val mode = Master

        assert(treeEventLogAsString.nonEmpty)
        assert(treeEventLog.id.nonEmpty)
        assert(treeEventLog.customerId == mode.customerId)
        assert(treeEventLog.serviceClass == mode.serviceClass)
        assert(treeEventLog.category == mode.category)
        assert(treeEventLog.signature == SigningHelper.signAndGetAsHex(config, SigningHelper.getBytesFromString(node.toString)))
        assert(treeEventLog.event == node)
        assert(treeEventLog.headers == Headers.create(
          HeaderNames.TRACE -> mode.value,
          HeaderNames.ORIGIN -> mode.category,
          TreeMonitor.headersNormalCreationFromMode(mode)
        ))
        assert(treeEventLog.id == chainer.getNode.map(_.value).getOrElse("NO_ID"))
        assert(treeEventLog.lookupKeys ==
          Seq(LookupKey(
            mode.lookupName,
            mode.category,
            treeEventLog.id.asKeyWithLabel(mode.category),
            chainer.seeds.map(x => x.id.asValueWithLabel(x.category))
          )))
        assert(treeEventLog.category == treeEventLog.lookupKeys.headOption.map(_.category).getOrElse("No CAT"))
        assert(events.map(_.id).sorted == chainer.seeds.map(_.id).sorted)
        assert(events.size == chainer.seeds.size)
        assert(events.size == treeEventLog.lookupKeys.flatMap(_.value).size)
        assert(maxNumberToRead == messages.size)
        assert(chainer.getNodes.map(_.value).size == customerIds.size)
        assert(chainer.getHashes.flatten.size == events.size)

      }

    }

    "consume, process and publish tree and event logs in Slave splitting" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort
      val InjectorHelper = new InjectorHelperImpl(bootstrapServers, messageEnvelopeTopic, eventLogTopic, split = true)
      val config = InjectorHelper.get[Config]

      withRunningKafka {

        val customerIds = List("Sun")
        val customerRange = 0 to 400

        val events = customerIds.flatMap { x =>
          customerRange.map(_ =>

            EventLog(ChainerJsonSupport.ToJson[ProtocolMessage](PMHelper.createPM).get)
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

        Thread.sleep(7000)

        val maxNumberToRead = events.sliding(50, 50).size /* tree */
        val messages = consumeNumberStringMessagesFrom(eventLogTopic, maxNumberToRead)

        assert(messages.size == events.sliding(50, 50).size)

      }

    }

    "consume, process and publish tree and event logs in Slave splitting and checking upgrade tree as Slave" in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort
      val InjectorHelper = new InjectorHelperImpl(bootstrapServers, messageEnvelopeTopic, eventLogTopic, split = true, treeEvery = 10, treeUpgrade = 13)
      val config = InjectorHelper.get[Config]

      withRunningKafka {

        val customerIds = List("Sun")
        val customerRange = 0 to 400

        val events = customerIds.flatMap { x =>
          customerRange.map(_ =>

            EventLog(ChainerJsonSupport.ToJson[ProtocolMessage](PMHelper.createPM).get)
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
        val treeMonitor = InjectorHelper.get[TreeMonitor]
        treeMonitor.start
        //Consumer

        Thread.sleep(7000)

        val maxNumberToRead = events.sliding(50, 50).size /* tree */
        val messages = consumeNumberStringMessagesFrom(eventLogTopic, maxNumberToRead)
        val messagesAsEventLogs = messages.map(x => ChainerJsonSupport.FromString[EventLog](x).get)

        val mode = Slave
        val expectedHeaders = Headers.create(
          HeaderNames.TRACE -> mode.value,
          HeaderNames.ORIGIN -> mode.category,
          TreeMonitor.headersNormalCreationFromMode(mode)
        )

        messagesAsEventLogs.map(_.headers).map { x =>
          assert(x == expectedHeaders)
        }

        assert(messages.size == events.sliding(50, 50).size)

        val upgrade = consumeFirstStringMessageFrom(eventLogTopic)
        val upgradeEventLog = ChainerJsonSupport.FromString[EventLog](upgrade).get

        val expectedHeadersUpgrade = Headers.create(
          HeaderNames.TRACE -> mode.value,
          HeaderNames.ORIGIN -> mode.category
        //, TreeMonitor.headerExcludeStorage
        )

        assert(upgradeEventLog.headers == expectedHeadersUpgrade)

        assert(messagesAsEventLogs.reverse.headOption.map(_.id) == Option(upgradeEventLog.id))

        Thread.sleep(10000)

        assertThrows[java.util.concurrent.TimeoutException](consumeFirstStringMessageFrom(eventLogTopic))

      }

    }

    "consume, process and publish tree and event logs in Slave splitting and checking upgrade tree as Master" taggedAs (new Tag("problem2")) in {

      implicit val kafkaConfig: EmbeddedKafkaConfig = EmbeddedKafkaConfig(kafkaPort = PortGiver.giveMeKafkaPort, zooKeeperPort = PortGiver.giveMeZookeeperPort)

      val bootstrapServers = "localhost:" + kafkaConfig.kafkaPort
      val InjectorHelper = new InjectorHelperImpl(
        bootstrapServers,
        messageEnvelopeTopic,
        eventLogTopic,
        split = true,
        treeEvery = 10,
        treeUpgrade = 13,
        mode = Master,
        webClientProvider = Option(new WebClientProvider)
      )
      val config = InjectorHelper.get[Config]

      withRunningKafka {

        val customerIds = List("Sun")
        val customerRange = 0 to 400

        val events = customerIds.flatMap { x =>
          customerRange.map(_ =>

            EventLog(ChainerJsonSupport.ToJson[ProtocolMessage](PMHelper.createPM).get)
              .withEventTime(new Date())
              .withRandomNonce
              .withCustomerId(x)
              .withNewId
              .withCategory(Values.SLAVE_TREE_CATEGORY)
              .sign(config))
        }

        events.foreach(x => publishStringMessageToKafka(messageEnvelopeTopic, x.toJson))

        //Consumer
        val treeMonitor = InjectorHelper.get[TreeMonitor]
        treeMonitor.start

        val consumer = InjectorHelper.get[StringConsumer]
        consumer.setTopics(Set(messageEnvelopeTopic))
        consumer.setConsumptionStrategy(All)
        consumer.startPolling()
        //Consumer

        val bigBangTree = 1
        val maxNumberToReadNormalMasters = events.sliding(50, 50).size /* tree */
        val upgradeTree = 1

        val maxToRead = bigBangTree + maxNumberToReadNormalMasters + upgradeTree

        Thread.sleep(10000)

        val messages = consumeNumberStringMessagesFrom(eventLogTopic, maxToRead)
        val messagesAsEventLogs = messages.map(x => ChainerJsonSupport.FromString[EventLog](x).get)

        val mode = Master
        val expectedHeadersBigBang = Headers.create(
          HeaderNames.TRACE -> mode.value,
          HeaderNames.ORIGIN -> mode.category
        )

        val expectedHeaders = Headers.create(
          HeaderNames.TRACE -> mode.value,
          HeaderNames.ORIGIN -> mode.category,
          TreeMonitor.headersNormalCreationFromMode(mode)
        )

        //big bang
        val bigBang = messagesAsEventLogs.headOption

        //normal trees
        val normalTrees =
          messagesAsEventLogs
            .tail
            .reverse
            .tail
            .reverse

        val upgradeTREE = messagesAsEventLogs
          .tail
          .reverse
          .headOption

        bigBang
          .map { x =>
            assert(x.headers == expectedHeadersBigBang)
          }

        normalTrees
          .map { x =>
            assert(x.headers == expectedHeaders)
          }

        upgradeTREE
          .map { x =>
            assert(x.headers == expectedHeadersBigBang)
          }

        //upgrade tree

        assert(messages.size == maxToRead)

        for {
          l <- normalTrees.reverse.headOption
          u <- upgradeTREE
        } yield {
          assert(l.id == u.id)
          assert(l.headers != u.headers)
          assert(l.lookupKeys != u.lookupKeys)
        }

      }

    }

  }

  override protected def beforeEach(): Unit = {
    CollectorRegistry.defaultRegistry.clear()
  }

}
