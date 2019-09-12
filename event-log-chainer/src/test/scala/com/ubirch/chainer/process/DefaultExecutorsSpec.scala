package com.ubirch.chainer.process

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.TestBase
import com.ubirch.chainer.models.{ Chainer, Master, Slave }
import com.ubirch.chainer.services.kafka.consumer.ChainerPipeData
import com.ubirch.chainer.services.metrics.{ DefaultLeavesCounter, DefaultTreeCounter }
import com.ubirch.chainer.services.{ AtomicInstantMonitor, InstantMonitor, TreeCache }
import com.ubirch.chainer.util._
import com.ubirch.kafka.util.Exceptions.NeedForPauseException
import com.ubirch.models.{ EventLog, MemCache }
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.services.config.ConfigProvider
import com.ubirch.services.execution.ExecutionProvider
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.util.{ SigningHelper, UUIDHelper }
import io.prometheus.client.CollectorRegistry
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.json4s.JValue
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps

class DefaultExecutorsSpec extends TestBase with MockitoSugar with LazyLogging {

  val config: Config = new ConfigProvider {} get ()

  implicit val ec: ExecutionContext = new ExecutionProvider(config) {} get ()

  "FilterEmpty" must {

    "fail if all values are empty" in {

      val instantMonitor: InstantMonitor = new AtomicInstantMonitor

      val filterEmpty = new FilterEmpty(instantMonitor, config)
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

      val instantMonitor: InstantMonitor = new AtomicInstantMonitor

      val filterEmpty = new FilterEmpty(instantMonitor, config)
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

      Thread.sleep(3000)

      val filterEmpty = new FilterEmpty(instantMonitor, config)

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

      val instantMonitor: InstantMonitor = new AtomicInstantMonitor

      val filterEmpty = new FilterEmpty(instantMonitor, config)
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
        val el = EventLog(eventData)
        new ConsumerRecord[String, String]("my_topic", 1, 1, "", el.toJson)
      }.toVector

      val cp = ChainerPipeData(consumerRecords, Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty)

      val els = eventLogsParser(Future.successful(cp))

      val res = await(els, 2 seconds)

      assert(res.eventLogs.nonEmpty)
      assert(consumerRecords.size == res.eventLogs.size)
      assert(consumerRecords == res.consumerRecords)
      assert(res.treeEventLogs.isEmpty)
      assert(res.chainers.isEmpty)
      assert(res.producerRecords.isEmpty)
      assert(res.recordsMetadata.isEmpty)

    }

    "fail when all values fail to get parsed" in {

      val reporter = mock[Reporter]

      val eventLogsParser = new EventLogsParser(reporter)

      val consumerRecords = (0 to 10).map { _ =>
        new ConsumerRecord[String, String]("my_topic", 1, 1, "", "")
      }.toVector

      val cp = ChainerPipeData(consumerRecords, Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty)

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
        val el = EventLog(eventData)
        new ConsumerRecord[String, String]("my_topic", 1, 1, "", el.toJson)
      }.toVector

      val cp = ChainerPipeData(consumerRecords, Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty)

      val els = eventLogsSigner(eventLogsParser(Future.successful(cp)))

      val res = await(els, 2 seconds)

      assert(res.eventLogs.nonEmpty)
      assert(res.eventLogs.exists(x => x.signature == SigningHelper.signAndGetAsHex(config, SigningHelper.getBytesFromString(eventData.toString))))
      assert(consumerRecords.size == res.eventLogs.size)
      assert(consumerRecords == res.consumerRecords)
      assert(res.treeEventLogs.isEmpty)
      assert(res.chainers.isEmpty)
      assert(res.producerRecords.isEmpty)
      assert(res.recordsMetadata.isEmpty)

    }

    "fail if nothing to sign" in {

      import org.json4s.jackson.JsonMethods.parse

      val reporter = mock[Reporter]

      val eventLogsSigner = new EventLogsSigner(reporter, config)

      val eventData: JValue = parse("""{ "numbers" : [1, 2, 3, 4] }""")

      val consumerRecords = (0 to 10).map { _ =>
        val el = EventLog(eventData)
        new ConsumerRecord[String, String]("my_topic", 1, 1, "", el.toJson)
      }.toVector

      val cp = ChainerPipeData(consumerRecords, Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty)

      val els = eventLogsSigner(Future.successful(cp))

      def res = await(els, 2 seconds)

      assertThrows[SigningEventLogException](res)

    }

  }

  "TreeCreatorExecutor" must {
    "create tree" in {
      import org.json4s.jackson.JsonMethods.parse

      val reporter = mock[Reporter]

      val eventPreparer = new EventLogsParser(reporter) andThen new EventLogsSigner(reporter, config)

      val _balancingHash = Chainer.getEmptyNodeVal

      val memCache = new MemCache
      val treeCache = new TreeCache(memCache, config)

      val treeCreatorExecutor = new TreeCreatorExecutor(config, treeCache) {
        override def outerBalancingHash: Option[String] = Option(_balancingHash)
      }

      val eventData: JValue = parse("""{ "numbers" : [1, 2, 3, 4] }""")

      val consumerRecords = (0 to 10).map { _ =>
        val el = EventLog(eventData)
        new ConsumerRecord[String, String]("my_topic", 1, 1, "", el.toJson)
      }.toVector

      val cp = ChainerPipeData(consumerRecords, Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty)

      val els = eventPreparer(Future.successful(cp))

      def chainerRes = treeCreatorExecutor(els)

      val res = await(chainerRes, 2 seconds)

      import com.ubirch.chainer.models.Chainables.eventLogChainable

      val eventLogChainer = new Chainer(res.eventLogs.toList) {
        override def balancingHash: String = _balancingHash
      }
        .createGroups
        .createSeedHashes
        .createSeedNodes(keepOrder = true)
        .createNode

      assert(res.chainers.nonEmpty)
      assert(res.chainers.map(x => ChainerJsonSupport.ToJson(x.getNode).get) == Vector(ChainerJsonSupport.ToJson(eventLogChainer.getNode).get))
      assert(res.chainers.flatMap(_.getNode) == eventLogChainer.getNode.toVector)

    }
  }

  "TreeEventLogCreation" must {
    "create slave tree" in {

      val reporter = mock[Reporter]

      val eventPreparer = new EventLogsParser(reporter) andThen new EventLogsSigner(reporter, config)

      val _balancingHash = Chainer.getEmptyNodeVal

      val memCache = new MemCache
      val treeCache = new TreeCache(memCache, config)

      val treeCreatorExecutor = new TreeCreatorExecutor(config, treeCache) {
        override def outerBalancingHash: Option[String] = Option(_balancingHash)
      }

      val eventData: JValue = ChainerJsonSupport.ToJson[ProtocolMessage](PMHelper.createPM).get

      val consumerRecords = (0 to 10).map { _ =>
        val el = EventLog(eventData)
        new ConsumerRecord[String, String]("my_topic", 1, 1, "", el.toJson)
      }.toVector

      val cp = ChainerPipeData(consumerRecords, Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty)

      val els = eventPreparer(Future.successful(cp))

      def chainerRes = treeCreatorExecutor(els)

      val res = await(chainerRes, 2 seconds)

      import com.ubirch.chainer.models.Chainables.eventLogChainable

      val eventLogChainer = new Chainer(res.eventLogs.toList) {
        override def balancingHash: String = _balancingHash
      }
        .createGroups
        .createSeedHashes
        .createSeedNodes(keepOrder = true)
        .createNode

      val treeEventLogCreation = new TreeEventLogCreation(config, new DefaultTreeCounter(config), new DefaultLeavesCounter(config))

      val treeEventLogRes = await(treeEventLogCreation(chainerRes), 2 seconds)

      assert(res.chainers.nonEmpty)
      assert(res.chainers.map(x => ChainerJsonSupport.ToJson(x.getNode).get) == Vector(ChainerJsonSupport.ToJson(eventLogChainer.getNode).get))
      assert(res.chainers.flatMap(_.getNode) == eventLogChainer.getNode.toVector)
      assert(treeEventLogRes.treeEventLogs.nonEmpty)
      assert(treeEventLogRes.treeEventLogs.map(_.category).forall(x => x == Slave.category))
      assert(treeEventLogRes.treeEventLogs.map(_.serviceClass).forall(x => x == Slave.serviceClass))
      assert(treeEventLogRes.treeEventLogs.map(_.customerId).forall(x => x == Slave.customerId))
      assert(treeEventLogRes.treeEventLogs.flatMap(_.lookupKeys).forall(_.name == Slave.lookupName))
      assert(treeEventLogCreation.mode == Slave)

    }

    "create master tree" in {
      import org.json4s.jackson.JsonMethods.parse

      val reporter = mock[Reporter]

      val eventPreparer = new EventLogsParser(reporter) andThen new EventLogsSigner(reporter, config)

      val _balancingHash = Chainer.getEmptyNodeVal

      val memCache = new MemCache
      val treeCache = new TreeCache(memCache, config)

      val treeCreatorExecutor = new TreeCreatorExecutor(config, treeCache) {
        override def outerBalancingHash: Option[String] = Option(_balancingHash)
      }

      val eventData: JValue = parse("""{ "numbers" : [1, 2, 3, 4] }""")

      val consumerRecords = (0 to 10).map { _ =>
        val el = EventLog(eventData)
        new ConsumerRecord[String, String]("my_topic", 1, 1, "", el.toJson)
      }.toVector

      val cp = ChainerPipeData(consumerRecords, Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty)

      val els = eventPreparer(Future.successful(cp))

      def chainerRes = treeCreatorExecutor(els)

      val res = await(chainerRes, 2 seconds)

      import com.ubirch.chainer.models.Chainables.eventLogChainable

      val eventLogChainer = new Chainer(res.eventLogs.toList) {
        override def balancingHash: String = _balancingHash
      }
        .createGroups
        .createSeedHashes
        .createSeedNodes(keepOrder = true)
        .createNode

      val treeEventLogCreation = new TreeEventLogCreation(config, new DefaultTreeCounter(config), new DefaultLeavesCounter(config)) {
        override def modeFromConfig: String = Master.value
      }

      val treeEventLogRes = await(treeEventLogCreation(chainerRes), 2 seconds)

      assert(res.chainers.nonEmpty)
      assert(res.chainers.map(x => ChainerJsonSupport.ToJson(x.getNode).get) == Vector(ChainerJsonSupport.ToJson(eventLogChainer.getNode).get))
      assert(res.chainers.flatMap(_.getNode) == eventLogChainer.getNode.toVector)
      assert(treeEventLogRes.treeEventLogs.nonEmpty)
      assert(treeEventLogRes.treeEventLogs.map(_.category).forall(x => x == Master.category))
      assert(treeEventLogRes.treeEventLogs.map(_.serviceClass).forall(x => x == Master.serviceClass))
      assert(treeEventLogRes.treeEventLogs.map(_.customerId).forall(x => x == Master.customerId))
      assert(treeEventLogRes.treeEventLogs.flatMap(_.lookupKeys).forall(_.name == Master.lookupName))
      assert(treeEventLogCreation.mode == Master)

    }
  }

  override protected def beforeEach(): Unit = {
    CollectorRegistry.defaultRegistry.clear()
  }

}
