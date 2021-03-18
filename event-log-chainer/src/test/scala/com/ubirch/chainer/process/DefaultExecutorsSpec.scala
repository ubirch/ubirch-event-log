package com.ubirch.chainer.process

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.TestBase
import com.ubirch.chainer.models.Chainer.CreateConfig
import com.ubirch.chainer.models.{ Chainer, Master, Slave }
import com.ubirch.chainer.services._
import com.ubirch.chainer.services.httpClient.DefaultAsyncWebClient
import com.ubirch.chainer.services.kafka.consumer.ChainerPipeData
import com.ubirch.chainer.services.metrics.{ DefaultLeavesCounter, DefaultTreeCounter }
import com.ubirch.chainer.services.tree._
import com.ubirch.chainer.util._
import com.ubirch.kafka.util.Exceptions.NeedForPauseException
import com.ubirch.models.{ EventLog, Values }
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.ExecutionProvider
import com.ubirch.services.kafka.producer.{ DefaultStringProducer, Reporter }
import com.ubirch.services.lifeCycle.DefaultLifecycle
import com.ubirch.services.metrics.{ DefaultFailureCounter, DefaultSuccessCounter }
import com.ubirch.util.{ SigningHelper, UUIDHelper }
import io.prometheus.client.CollectorRegistry
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.json4s.JValue
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps

class DefaultExecutorsSpec extends TestBase with MockitoSugar with LazyLogging {

  val config: Config = new ConfigProvider {} get ()

  implicit val ec: ExecutionContext = new ExecutionProvider(config) {} get ()

  "FilterEmpty" must {

    "fail if all values are empty" in {

      val treeMonitor = mock[TreeMonitor]

      val filterEmpty = new FilterEmpty(treeMonitor, config)
      val res = filterEmpty(Vector.empty)

      assertThrows[EmptyValueException](await(res, 2 seconds))

    }

    "succeed if non empty and threshold was reached" in {

      val uuid = UUIDHelper.timeBasedUUID

      val data = (0 to 10).map { _ =>
        new ConsumerRecord[String, String]("my_topic", 1, 1, "", uuid.toString)
      } ++ (0 to 10).map { _ =>
        new ConsumerRecord[String, String]("my_topic", 1, 1, "", "")
      }

      val treeMonitor = mock[TreeMonitor]
      when(treeMonitor.goodToCreate(any[Vector[ConsumerRecord[String, String]]]())).thenReturn(true)

      val filterEmpty = new FilterEmpty(treeMonitor, config)
      val fres = filterEmpty(data.toVector)

      lazy val res = await(fres, 2 seconds)

      assert(res.isInstanceOf[ChainerPipeData])

    }

    "instant monitor should reset after filtering" in {

      val uuid = UUIDHelper.timeBasedUUID

      val data = (0 to 10).map { _ =>
        new ConsumerRecord[String, String]("my_topic", 1, 1, "", uuid.toString)
      } ++ (0 to 10).map { _ =>
        new ConsumerRecord[String, String]("my_topic", 1, 1, "", "")
      }

      val instantMonitor: InstantMonitor = new AtomicInstantMonitor
      val treeCreationTrigger = new TreeCreationTrigger(instantMonitor, config)

      val treeMonitor = mock[TreeMonitor]

      when(treeMonitor.treeCreationTrigger).thenReturn(treeCreationTrigger)
      when(treeMonitor.goodToCreate(any[Vector[ConsumerRecord[String, String]]]())).thenCallRealMethod()

      Thread.sleep(3000)

      val filterEmpty = new FilterEmpty(treeMonitor, config)

      val cim0 = instantMonitor.elapsedSeconds

      val fres = filterEmpty(data.toVector)

      val res = await(fres, 2 seconds)

      val cim1 = instantMonitor.elapsedSeconds

      assert(cim0 > cim1)
      assert(cim1 == 0)
      assert(res.isInstanceOf[ChainerPipeData])

    }

    "fail if threshold hasn't been reached" in {

      val uuid = UUIDHelper.timeBasedUUID

      val data = (0 to 7).map { _ =>
        new ConsumerRecord[String, String]("my_topic", 1, 1, "", uuid.toString)
      }

      val treeMonitor = mock[TreeMonitor]

      val filterEmpty = new FilterEmpty(treeMonitor, config)
      val fres = filterEmpty(data.toVector)

      lazy val res = await(fres, 2 seconds)

      assertThrows[NeedForPauseException](res)

    }

  }

  "EventLogsParser" must {

    "succeed when normal conditions are met" in {

      import org.json4s.jackson.JsonMethods.parse

      val reporter = mock[Reporter]

      val eventLogsParser = new EventLogsParser(reporter)

      val eventData: JValue = parse("""{ "numbers" : [1, 2, 3, 4] }""")

      val consumerRecords = (0 to 10).map { _ =>
        val el = EventLog(eventData).withNewId.withRandomNonce
        new ConsumerRecord[String, String]("my_topic", 1, 1, "", el.toJson)
      }.toVector

      val cp = ChainerPipeData(consumerRecords, Vector.empty, Vector.empty, Vector.empty, Vector.empty)

      val els = eventLogsParser(Future.successful(cp))

      val res = await(els, 2 seconds)

      assert(res.eventLogs.nonEmpty)
      assert(consumerRecords.size == res.eventLogs.size)
      assert(consumerRecords == res.consumerRecords)
      assert(res.treeEventLogs.isEmpty)
      assert(res.chainers.isEmpty)
      assert(res.recordsMetadata.isEmpty)

    }

    "fail when all values fail to get parsed" in {

      val reporter = mock[Reporter]

      val eventLogsParser = new EventLogsParser(reporter)

      val consumerRecords = (0 to 10).map { _ =>
        new ConsumerRecord[String, String]("my_topic", 1, 1, "", "")
      }.toVector

      val cp = ChainerPipeData(consumerRecords, Vector.empty, Vector.empty, Vector.empty, Vector.empty)

      val els = eventLogsParser(Future.successful(cp))

      lazy val res = await(els, 2 seconds)

      assertThrows[ParsingIntoEventLogException](res)

    }

  }

  "EventLogsSigner" must {

    "succeed when normal conditions are met" in {

      import org.json4s.jackson.JsonMethods.parse

      val reporter = mock[Reporter]

      val eventLogsParser = new EventLogsParser(reporter)

      val eventLogsSigner = new EventLogsSigner(reporter, config)

      val eventData: JValue = parse("""{ "numbers" : [1, 2, 3, 4] }""")

      val consumerRecords = (0 to 10).map { _ =>
        val el = EventLog(eventData).withNewId.withRandomNonce
        new ConsumerRecord[String, String]("my_topic", 1, 1, "", el.toJson)
      }.toVector

      val cp = ChainerPipeData(consumerRecords, Vector.empty, Vector.empty, Vector.empty, Vector.empty)

      val els = eventLogsSigner(eventLogsParser(Future.successful(cp)))

      val res = await(els, 2 seconds)

      assert(res.eventLogs.nonEmpty)
      assert(res.eventLogs.exists(x => x.signature == SigningHelper.signAndGetAsHex(config, SigningHelper.getBytesFromString(eventData.toString))))
      assert(consumerRecords.size == res.eventLogs.size)
      assert(consumerRecords == res.consumerRecords)
      assert(res.treeEventLogs.isEmpty)
      assert(res.chainers.isEmpty)
      assert(res.recordsMetadata.isEmpty)

    }

    "fail if nothing to sign" in {

      import org.json4s.jackson.JsonMethods.parse

      val reporter = mock[Reporter]

      val eventLogsSigner = new EventLogsSigner(reporter, config)

      val eventData: JValue = parse("""{ "numbers" : [1, 2, 3, 4] }""")

      val consumerRecords = (0 to 10).map { _ =>
        val el = EventLog(eventData).withNewId.withRandomNonce
        new ConsumerRecord[String, String]("my_topic", 1, 1, "", el.toJson)
      }.toVector

      val cp = ChainerPipeData(consumerRecords, Vector.empty, Vector.empty, Vector.empty, Vector.empty)

      val els = eventLogsSigner(Future.successful(cp))

      def res = await(els, 2 seconds)

      assertThrows[SigningEventLogException](res)

    }

  }

  "TreeCreatorExecutor" must {

    "create tree" in {

      val reporter = mock[Reporter]

      val eventPreparer = new EventLogsParser(reporter) andThen new EventLogsSigner(reporter, config)

      val _balancingHash = Chainer.getEmptyNodeVal

      val treeCache = new TreeCache(config)

      val treeCreator = new TreeCreator(config) {
        override def outerBalancingHash: Option[String] = Option(_balancingHash)
      }

      val treeEventLogCreator = new TreeEventLogCreator(config, new DefaultTreeCounter(config), new DefaultLeavesCounter(config))

      val stringProducer = new DefaultStringProducer(config, new DefaultLifecycle())

      val successCounter = new DefaultSuccessCounter(config)
      val failureCounter = new DefaultFailureCounter(config)
      val treePublisher = new TreePublisher(stringProducer.get(), successCounter, failureCounter, config)

      val treeCreationTrigger = new TreeCreationTrigger(new AtomicInstantMonitor, config)

      val treeUpgrade = new TreeUpgrade(new AtomicInstantMonitor, config)

      val webClient = new DefaultAsyncWebClient()

      val treeBootstrap = new TreeWarmUp(treeCache, webClient, config)

      val treeMonitor = new TreeMonitor(treeBootstrap, treeCache, treeCreator, treeEventLogCreator, treePublisher, treeCreationTrigger, treeUpgrade, config)

      val treeCreatorExecutor = new TreeCreatorExecutor(treeMonitor)

      val eventData: JValue = ChainerJsonSupport.ToJson[ProtocolMessage](PMHelper.createPM).get

      val consumerRecords = (0 to 10).map { _ =>
        val el = EventLog(eventData).withNewId.withRandomNonce
        new ConsumerRecord[String, String]("my_topic", 1, 1, "", el.toJson)
      }.toVector

      val cp = ChainerPipeData(consumerRecords, Vector.empty, Vector.empty, Vector.empty, Vector.empty)

      val els = eventPreparer(Future.successful(cp))

      def chainerRes = treeCreatorExecutor(els)

      val res = await(chainerRes, 2 seconds)

      import com.ubirch.chainer.models.Chainables.eventLogChainable

      val eventLogChainer = Chainer(res.eventLogs.toList)
        .withMergerFunc(Hasher.mergeAndHash)
        .withBalancerFunc(_ => _balancingHash)
        .withHashZero(None)
        .withGeneralGrouping
        .createSeedHashes
        .createSeedNodes(keepOrder = true)
        .createNode

      assert(res.chainers.nonEmpty)
      assert(res.chainers.map(x => ChainerJsonSupport.ToJson(x.getNode).get) == Vector(ChainerJsonSupport.ToJson(eventLogChainer.getNode).get))
      assert(res.chainers.flatMap(_.getNode) == eventLogChainer.getNode.toVector)

    }

    "create tree with chained hashes" in {

      val reporter = mock[Reporter]

      val eventPreparer = new EventLogsParser(reporter) andThen new EventLogsSigner(reporter, config)

      val _balancingHash = Chainer.getEmptyNodeVal

      val treeCache = new TreeCache(config)

      val treeCreator = new TreeCreator(config) {
        override def outerBalancingHash: Option[String] = Option(_balancingHash)
      }

      val treeEventLogCreator = new TreeEventLogCreator(config, new DefaultTreeCounter(config), new DefaultLeavesCounter(config))

      val stringProducer = new DefaultStringProducer(config, new DefaultLifecycle())

      val successCounter = new DefaultSuccessCounter(config)
      val failureCounter = new DefaultFailureCounter(config)
      val treePublisher = new TreePublisher(stringProducer.get(), successCounter, failureCounter, config)

      val treeCreationTrigger = new TreeCreationTrigger(new AtomicInstantMonitor, config)

      val treeUpgrade = new TreeUpgrade(new AtomicInstantMonitor, config)

      val webClient = new DefaultAsyncWebClient()

      val treeBootstrap = new TreeWarmUp(treeCache, webClient, config)

      val treeMonitor = new TreeMonitor(treeBootstrap, treeCache, treeCreator, treeEventLogCreator, treePublisher, treeCreationTrigger, treeUpgrade, config)

      val treeCreatorExecutor = new TreeCreatorExecutor(treeMonitor)

      def eventData: JValue = ChainerJsonSupport.ToJson[ProtocolMessage](PMHelper.createPM).get

      val consumerRecords = (0 to 200).map { _ =>
        val el = EventLog(eventData).withNewId.withRandomNonce
        new ConsumerRecord[String, String]("my_topic", 1, 1, "", el.toJson)
      }.toVector

      val cp = ChainerPipeData(consumerRecords, Vector.empty, Vector.empty, Vector.empty, Vector.empty)

      val els = eventPreparer(Future.successful(cp))
      val nels = await(els, 5 second)

      def chainerRes = treeCreatorExecutor(els)

      val res = await(chainerRes, 5 seconds)
      val eventLogChainer = treeCreator.create(nels.eventLogs.toList, None)(treeCache.prefix)

      import com.ubirch.chainer.models.Chainables.eventLogChainable

      val createConfig = CreateConfig[String](
        None,
        treeCreator.splitTrees,
        treeCreator.splitSize,
        treeCache.prefix,
        Hasher.mergeAndHash,
        _ => _balancingHash
      )
      val chainerRes2 = Chainer.create(nels.eventLogs.toList, createConfig)

      assert(eventLogChainer._2 == Option(treeCache.prefix(res.treeEventLogs.reverse.headOption.map(_.id).getOrElse("HOO"))))
      assert(eventLogChainer._2 == chainerRes2._2)

      assert(res.chainers.map(_.getNode).toList == eventLogChainer._1.map(_.getNode))
      assert(chainerRes2._1.map(_.getNode) == eventLogChainer._1.map(_.getNode))

      assert(res.chainers.nonEmpty)
      assert(res.chainers.map(x => ChainerJsonSupport.ToJson(x.getNode).get) == eventLogChainer._1.map(_.getNode).map(x => ChainerJsonSupport.ToJson(x).get).toVector)

    }

    "create slave tree" in {

      val reporter = mock[Reporter]

      val eventPreparer = new EventLogsParser(reporter) andThen new EventLogsSigner(reporter, config)

      val _balancingHash = Chainer.getEmptyNodeVal

      val treeCache = new TreeCache(config)

      val treeCreator = new TreeCreator(config) {
        override def outerBalancingHash: Option[String] = Option(_balancingHash)
      }

      val treeEventLogCreator = new TreeEventLogCreator(config, new DefaultTreeCounter(config), new DefaultLeavesCounter(config))

      val stringProducer = new DefaultStringProducer(config, new DefaultLifecycle())

      val successCounter = new DefaultSuccessCounter(config)
      val failureCounter = new DefaultFailureCounter(config)
      val treePublisher = new TreePublisher(stringProducer.get(), successCounter, failureCounter, config)

      val treeCreationTrigger = new TreeCreationTrigger(new AtomicInstantMonitor, config)

      val treeUpgrade = new TreeUpgrade(new AtomicInstantMonitor, config)

      val webClient = new DefaultAsyncWebClient()

      val treeBootstrap = new TreeWarmUp(treeCache, webClient, config)

      val treeMonitor = new TreeMonitor(treeBootstrap, treeCache, treeCreator, treeEventLogCreator, treePublisher, treeCreationTrigger, treeUpgrade, config)

      val treeCreatorExecutor = new TreeCreatorExecutor(treeMonitor)

      val eventData: JValue = ChainerJsonSupport.ToJson[ProtocolMessage](PMHelper.createPM).get

      val consumerRecords = (0 to 10).map { _ =>
        val el = EventLog(eventData).withNewId.withRandomNonce
        new ConsumerRecord[String, String]("my_topic", 1, 1, "", el.toJson)
      }.toVector

      val cp = ChainerPipeData(consumerRecords, Vector.empty, Vector.empty, Vector.empty, Vector.empty)

      val els = eventPreparer(Future.successful(cp))

      def chainerRes = treeCreatorExecutor(els)

      val res = await(chainerRes, 2 seconds)

      import com.ubirch.chainer.models.Chainables.eventLogChainable

      val eventLogChainer = Chainer(res.eventLogs.toList)
        .withMergerFunc(Hasher.mergeAndHash)
        .withBalancerFunc(_ => _balancingHash)
        .createGroups
        .createSeedHashes
        .createSeedNodes(keepOrder = true)
        .createNode

      val treeEventLogCreation = new TreeCreatorExecutor(treeMonitor)

      val treeEventLogRes = await(treeEventLogCreation(chainerRes), 2 seconds)

      assert(res.chainers.nonEmpty)
      assert(res.chainers.map(x => ChainerJsonSupport.ToJson(x.getNode).get) == Vector(ChainerJsonSupport.ToJson(eventLogChainer.getNode).get))
      assert(res.chainers.flatMap(_.getNode) == eventLogChainer.getNode.toVector)
      assert(treeEventLogRes.treeEventLogs.nonEmpty)
      assert(treeEventLogRes.treeEventLogs.map(_.category).forall(x => x == Slave.category))
      assert(treeEventLogRes.treeEventLogs.map(_.serviceClass).forall(x => x == Slave.serviceClass))
      assert(treeEventLogRes.treeEventLogs.map(_.customerId).forall(x => x == Slave.customerId))
      assert(treeEventLogRes.treeEventLogs.flatMap(_.lookupKeys).forall(x => x.name == Slave.lookupName || x.name == Values.SLAVE_TREE_LINK_ID))
      assert(treeEventLogCreator.mode == Slave)

    }

    "create master tree" in {
      import org.json4s.jackson.JsonMethods.parse

      val reporter = mock[Reporter]

      val eventPreparer = new EventLogsParser(reporter) andThen new EventLogsSigner(reporter, config)

      val _balancingHash = Chainer.getEmptyNodeVal

      val treeCache = new TreeCache(config)

      val treeCreator = new TreeCreator(config) {
        override def outerBalancingHash: Option[String] = Option(_balancingHash)
      }

      val treeEventLogCreator = new TreeEventLogCreator(config, new DefaultTreeCounter(config), new DefaultLeavesCounter(config)) {
        override def modeFromConfig: String = Master.value
      }

      val stringProducer = new DefaultStringProducer(config, new DefaultLifecycle())

      val successCounter = new DefaultSuccessCounter(config)

      val failureCounter = new DefaultFailureCounter(config)

      val treePublisher = new TreePublisher(stringProducer.get(), successCounter, failureCounter, config)

      val treeCreationTrigger = new TreeCreationTrigger(new AtomicInstantMonitor, config)

      val treeUpgrade = new TreeUpgrade(new AtomicInstantMonitor, config)

      val webClient = new DefaultAsyncWebClient()

      val treeBootstrap = new TreeWarmUp(treeCache, webClient, config)

      val treeMonitor = new TreeMonitor(treeBootstrap, treeCache, treeCreator, treeEventLogCreator, treePublisher, treeCreationTrigger, treeUpgrade, config)

      val treeCreatorExecutor = new TreeCreatorExecutor(treeMonitor)

      val eventData: JValue = parse("""{ "numbers" : [1, 2, 3, 4] }""")

      val consumerRecords = (0 to 10).map { _ =>
        val el = EventLog(eventData).withNewId.withRandomNonce
        new ConsumerRecord[String, String]("my_topic", 1, 1, "", el.toJson)
      }.toVector

      val cp = ChainerPipeData(consumerRecords, Vector.empty, Vector.empty, Vector.empty, Vector.empty)

      val els = eventPreparer(Future.successful(cp))

      def chainerRes = treeCreatorExecutor(els)

      val res = await(chainerRes, 2 seconds)

      import com.ubirch.chainer.models.Chainables.eventLogChainable

      val eventLogChainer = Chainer(res.eventLogs.toList)
        .withMergerFunc(Hasher.mergeAndHash)
        .withBalancerFunc(_ => _balancingHash)
        .createGroups
        .createSeedHashes
        .createSeedNodes(keepOrder = true)
        .createNode

      val treeEventLogCreation = new TreeCreatorExecutor(treeMonitor)

      val treeEventLogRes = await(treeEventLogCreation(chainerRes), 2 seconds)

      assert(res.chainers.nonEmpty)
      assert(res.chainers.map(x => ChainerJsonSupport.ToJson(x.getNode).get) == Vector(ChainerJsonSupport.ToJson(eventLogChainer.getNode).get))
      assert(res.chainers.flatMap(_.getNode) == eventLogChainer.getNode.toVector)
      assert(treeEventLogRes.treeEventLogs.nonEmpty)
      assert(treeEventLogRes.treeEventLogs.map(_.category).forall(x => x == Master.category))
      assert(treeEventLogRes.treeEventLogs.map(_.serviceClass).forall(x => x == Master.serviceClass))
      assert(treeEventLogRes.treeEventLogs.map(_.customerId).forall(x => x == Master.customerId))
      assert(treeEventLogRes.treeEventLogs.flatMap(_.lookupKeys).forall(x => x.name == Master.lookupName || x.name == Values.MASTER_TREE_LINK_ID))
      assert(treeEventLogCreator.mode == Master)

    }
  }

  override protected def beforeEach(): Unit = {
    CollectorRegistry.defaultRegistry.clear()
  }

}
