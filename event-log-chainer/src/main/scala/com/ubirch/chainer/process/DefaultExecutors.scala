package com.ubirch.chainer.process

import java.util.UUID

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ProducerConfPaths
import com.ubirch.chainer.models.{ Chainer, Mode, Node, ValueStrategy }
import com.ubirch.chainer.services.InstantMonitor
import com.ubirch.chainer.services.kafka.consumer.ChainerPipeData
import com.ubirch.chainer.services.metrics.{ DefaultLeavesCounter, DefaultTreeCounter }
import com.ubirch.chainer.util._
import com.ubirch.kafka.producer.StringProducer
import com.ubirch.kafka.util.Exceptions.NeedForPauseException
import com.ubirch.models.EnrichedEventLog.enrichedEventLog
import com.ubirch.models.{ Error, EventLog, LookupKey }
import com.ubirch.process.Executor
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.services.metrics.{ Counter, DefaultMetricsLoggerCounter }
import com.ubirch.util.Implicits.enrichedConfig
import com.ubirch.util._
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps
import scala.util.{ Failure, Success, Try }

@Singleton
class FilterEmpty @Inject() (instantMonitor: InstantMonitor, config: Config)(implicit ec: ExecutionContext)
  extends Executor[Vector[ConsumerRecord[String, String]], Future[ChainerPipeData]]
  with LazyLogging {

  val minTreeRecords: Int = config.getInt("eventLog.minTreeRecords")
  val every: Int = config.getInt("eventLog.treeEvery")
  val pause: Int = config.getInt("eventLog.pause")

  logger.info("Min Tree Records [{}]  every [{}] seconds with [{}] pause millis", minTreeRecords, every, pause)

  override def apply(v1: Vector[ConsumerRecord[String, String]]): Future[ChainerPipeData] = Future {
    val records = v1.filter(_.value().nonEmpty)
    lazy val pd = ChainerPipeData(records, Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty)

    if (pause > 0) {
      if (records.nonEmpty) {

        val currentRecordsSize = records.size
        val currentElapsedSeconds = instantMonitor.elapsedSeconds
        if (currentRecordsSize >= minTreeRecords || currentElapsedSeconds >= every) {
          logger.debug("The chainer threshold HAS been reached. Current Records [{}]. Current Seconds Elapsed [{}]", currentRecordsSize, currentElapsedSeconds)
          instantMonitor.registerNewInstant
          pd
        } else {
          //logger.debug("The chainer threshold HASN'T been reached. Current Records [{}]. Current Seconds Elapsed [{}]", currentRecordsSize, currentElapsedSeconds)
          throw NeedForPauseException("Unreached Threshold", "The chainer threshold hasn't been reached yet", Some(pause millis))
        }

      } else {
        logger.error("No Records Found")
        throw EmptyValueException("No Records Found", pd)
      }
    } else {
      logger.error(s"Wrong params: Min Tree Records [$minTreeRecords]  every [$every] seconds with [$pause] pause millis")
      throw WrongParamsException(s"Wrong params: Min Tree Records [$minTreeRecords]  every [$every] seconds with [$pause] pause millis", pd)
    }
  }

}

@Singleton
class EventLogsParser @Inject() (reporter: Reporter)(implicit ec: ExecutionContext)
  extends Executor[Future[ChainerPipeData], Future[ChainerPipeData]]
  with LazyLogging {

  import reporter.Types._

  def uuid: UUID = UUIDHelper.timeBasedUUID

  override def apply(v1: Future[ChainerPipeData]): Future[ChainerPipeData] = v1.map { v1 =>

    val successfulEventLogs = scala.collection.mutable.ListBuffer.empty[EventLog]
    val errors = scala.collection.mutable.ListBuffer.empty[Error]

    v1.consumerRecords.filter(_.value().nonEmpty).foreach { x =>
      Try(ChainerJsonSupport.FromString[EventLog](x.value()).get) match {
        case Success(value) =>
          successfulEventLogs += value
        case Failure(exception) =>
          logger.error("Error Parsing Event (1): " + exception.getMessage)
          val e = ParsingIntoEventLogException(exception.getMessage, v1)
          val error = Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = x.value())
          errors += error
      }
    }

    errors.foreach(x => reporter.report(x))

    if (successfulEventLogs.nonEmpty) {
      v1.copy(eventLogs = successfulEventLogs.toVector)
    } else {
      logger.error("Error Parsing Event (2): " + v1)
      throw ParsingIntoEventLogException("Error Parsing Into Event Log", v1)
    }

  }

}

@Singleton
class EventLogsSigner @Inject() (reporter: Reporter, config: Config)(implicit ec: ExecutionContext)
  extends Executor[Future[ChainerPipeData], Future[ChainerPipeData]]
  with LazyLogging {

  import reporter.Types._

  def uuid: UUID = UUIDHelper.timeBasedUUID

  override def apply(v1: Future[ChainerPipeData]): Future[ChainerPipeData] = v1.map { v1 =>

    val signed = scala.collection.mutable.ListBuffer.empty[EventLog]
    val errors = scala.collection.mutable.ListBuffer.empty[Error]

    v1.eventLogs.foreach { x =>
      Try(x.sign(config)) match {
        case Success(value) => signed += value
        case Failure(exception) =>
          logger.error("Error Signing EventLog (1): " + exception.getMessage)
          val e = SigningEventLogException(exception.getMessage, v1)
          val error = Error(id = uuid, message = e.getMessage, exceptionName = e.name, value = x.toJson)
          errors += error
      }
    }

    errors.foreach(x => reporter.report(x))

    if (signed.nonEmpty) {
      v1.copy(eventLogs = signed.toVector)
    } else {
      logger.error("Error Signing EventLog (2): " + v1)
      throw SigningEventLogException("Nothing has been signed.", v1)
    }

  }
}

@Singleton
class TreeCreatorExecutor @Inject() (config: Config)(implicit ec: ExecutionContext)
  extends Executor[Future[ChainerPipeData], Future[ChainerPipeData]]
  with LazyLogging {

  def outerBalancingHash: Option[String] = None

  val splitTrees: Boolean = config.getBoolean("eventLog.split")

  override def apply(v1: Future[ChainerPipeData]): Future[ChainerPipeData] = v1.flatMap { v1 =>

    import com.ubirch.chainer.models.Chainables.eventLogChainable

    val subEventLogs: Iterator[Vector[EventLog]] = {
      if (splitTrees && v1.eventLogs.size >= 100) {
        v1.eventLogs.sliding(50, 50)
      } else {
        Iterator(v1.eventLogs)
      }
    }

    val futureChainers = subEventLogs.toVector.map { eventLogs =>
      Future {
        new Chainer(eventLogs.toList) {
          override def balancingHash: String = outerBalancingHash.getOrElse(super.balancingHash)
        }.createGroups
          .createSeedHashes
          .createSeedNodes(keepOrder = true)
          .createNode
      }
    }

    Future.sequence(futureChainers).map(chainers => v1.copy(chainers = chainers))

  }
}

@Singleton
class TreeEventLogCreation @Inject() (
    config: Config,
    @Named(DefaultTreeCounter.name) treeCounter: Counter,
    @Named(DefaultLeavesCounter.name) leavesCounter: Counter
)(implicit ec: ExecutionContext)
  extends Executor[Future[ChainerPipeData], Future[ChainerPipeData]]
  with ProducerConfPaths
  with LazyLogging {

  import LookupKey._

  def modeFromConfig: String = config.getString("eventLog.mode")
  def mode: Mode = Mode.getMode(modeFromConfig)

  lazy val sign: Boolean = config.getBoolean("eventLog.sign")

  logger.info("Tree EventLog Creator Mode: [{}]", mode.value)

  lazy val valuesStrategy = ValueStrategy.getStrategy(mode)

  def createEventLog(node: Node[String], els: Seq[EventLog]): EventLog = {
    val rootHash = node.value
    val data = ChainerJsonSupport.ToJson(node).get

    val category = mode.category
    val serviceClass = mode.serviceClass
    val lookupName = mode.lookupName
    val customerId = mode.customerId

    val lookupKeys = {
      LookupKey(
        lookupName,
        category,
        rootHash.asKeyWithLabel(category),
        els.flatMap(x => valuesStrategy.create(x))
      )
    }

    val treeEl = EventLog(data)
      .withNewId(rootHash)
      .withCategory(category)
      .withCustomerId(customerId)
      .withServiceClass(serviceClass)
      .withRandomNonce
      .addLookupKeys(lookupKeys)
      .addOriginHeader(category)
      .addTraceHeader(mode.value)

    if (sign) treeEl.sign(config)
    else treeEl

  }

  override def apply(v1: Future[ChainerPipeData]): Future[ChainerPipeData] = {

    v1.map { v1 =>

      val eventLogTrees = v1.chainers
        .flatMap { x => x.getNode.map(rn => (rn, x.es)) }
        .map { case (node, els) =>
          Try(createEventLog(node, els)) match {
            case Success(tree) =>
              val leavesSize = els.size
              logger.info(s"New [${mode.value}] tree($leavesSize) created, root hash is: ${tree.id}")
              treeCounter.counter.labels(tree.category).inc()
              leavesCounter.counter.labels(tree.category + "_LEAVES").inc(leavesSize)
              tree
            case Failure(e) =>
              logger.error(s"Error creating EventLog from [${mode.value}] (2): ", e)
              throw TreeEventLogCreationException(e.getMessage, v1)
          }
        }

      if (eventLogTrees.nonEmpty) {
        v1.copy(treeEventLogs = eventLogTrees)
      } else {
        throw TreeEventLogCreationException(s"Error creating EventLog from [${mode.value}] Chainer", v1)
      }

    }

  }
}

@Singleton
class CreateProducerRecords @Inject() (config: Config)(implicit ec: ExecutionContext)
  extends Executor[Future[ChainerPipeData], Future[ChainerPipeData]]
  with ProducerConfPaths
  with LazyLogging {

  val topic = config.getStringAsOption(TOPIC_PATH).getOrElse("com.ubirch.eventlog")

  override def apply(v1: Future[ChainerPipeData]): Future[ChainerPipeData] = {

    v1.map { v1 =>

      try {

        lazy val treeEventLogProducerRecord = v1.treeEventLogs.map { treeEl =>
          ProducerRecordHelper.toRecordFromEventLog(topic, v1.id.toString, treeEl)
        }

        if (treeEventLogProducerRecord.nonEmpty) {
          v1.copy(producerRecords = treeEventLogProducerRecord)
        } else {
          throw CreateTreeProducerRecordException("Error creating producer records", v1)
        }

      } catch {
        case e: Exception =>
          throw CreateTreeProducerRecordException(e.getMessage, v1)

      }

    }

  }
}

@Singleton
class Commit @Inject() (stringProducer: StringProducer, @Named(DefaultMetricsLoggerCounter.name) counter: Counter)(implicit ec: ExecutionContext)
  extends Executor[Future[ChainerPipeData], Future[ChainerPipeData]] with LazyLogging {

  override def apply(v1: Future[ChainerPipeData]): Future[ChainerPipeData] = {

    val futureMetadata = v1.map(_.producerRecords)
      .flatMap { prs =>
        Future.sequence {
          prs.map { x =>
            val futureResp = stringProducer.send(x)
            futureResp.onComplete {
              case Success(_) =>
                counter.counter.labels("success").inc()
              case Failure(exception) =>
                logger.error("Error publishing ", exception)
                counter.counter.labels("failure").inc()
            }
            futureResp
          }
        }
      }

    val futureResp = for {
      md <- futureMetadata
      v <- v1
    } yield {
      v.copy(recordsMetadata = md)
    }

    futureResp.recoverWith {
      case e: Exception =>
        v1.flatMap { x =>
          Future.failed {
            CommitException(e.getMessage, x)
          }
        }
    }

  }
}

/**
  * Represents a description of a family of executors that can be composed.
  */
trait ExecutorFamily {

  def filterEmpty: FilterEmpty

  def eventLogParser: EventLogsParser

  def treeCreatorExecutor: TreeCreatorExecutor

  def treeEventLogCreation: TreeEventLogCreation

  def createTreeProducerRecord: CreateProducerRecords

  def eventLogSigner: EventLogsSigner

  def commit: Commit

}

@Singleton
class DefaultExecutorFamily @Inject() (
    val filterEmpty: FilterEmpty,
    val eventLogParser: EventLogsParser,
    val treeCreatorExecutor: TreeCreatorExecutor,
    val treeEventLogCreation: TreeEventLogCreation,
    val createTreeProducerRecord: CreateProducerRecords,
    val eventLogSigner: EventLogsSigner,
    val commit: Commit
) extends ExecutorFamily
