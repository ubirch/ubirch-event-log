package com.ubirch.chainer.process

import java.util.UUID

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ProducerConfPaths
import com.ubirch.chainer.models.{ Chainer, Mode }
import com.ubirch.chainer.services.InstantMonitor
import com.ubirch.chainer.services.kafka.consumer.ChainerPipeData
import com.ubirch.chainer.services.metrics.DefaultTreeCounter
import com.ubirch.chainer.util._
import com.ubirch.kafka.util.Exceptions.NeedForPauseException
import com.ubirch.models.EnrichedEventLog.enrichedEventLog
import com.ubirch.models.{ Error, EventLog, LookupKey }
import com.ubirch.process.{ BasicCommit, Executor, MetricsLoggerBasic }
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.services.metrics.Counter
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

  logger.info("Min Tree Records [{}]  every [{}] seconds ", minTreeRecords, every)

  override def apply(v1: Vector[ConsumerRecord[String, String]]): Future[ChainerPipeData] = Future {
    val records = v1.filter(_.value().nonEmpty)
    lazy val pd = ChainerPipeData(records, Vector.empty, None, None, Vector.empty, Vector.empty)
    if (records.nonEmpty) {

      val currentRecordsSize = records.size
      val currentElapsedSeconds = instantMonitor.elapsedSeconds
      if (currentRecordsSize >= minTreeRecords || currentElapsedSeconds >= every) {
        logger.debug("The chainer threshold HAS been reached. Current Records [{}]. Current Seconds Elapsed [{}]", currentRecordsSize, currentElapsedSeconds)
        instantMonitor.registerNewInstant
        pd
      } else {
        //logger.debug("The chainer threshold HASN'T been reached. Current Records [{}]. Current Seconds Elapsed [{}]", currentRecordsSize, currentElapsedSeconds)
        throw NeedForPauseException("Unreached Threshold", "The chainer threshold hasn't been reached yet", Some(5 millis))
      }

    } else {
      logger.error("No Records Found")
      throw EmptyValueException("No Records Found", pd)
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
      Try(EventLogJsonSupport.FromString[EventLog](x.value()).get) match {
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

  override def apply(v1: Future[ChainerPipeData]): Future[ChainerPipeData] = v1.map { v1 =>

    import com.ubirch.chainer.models.Chainables.eventLogChainable

    val eventLogChainer = new Chainer(v1.eventLogs.toList) {
      override def balancingHash: String = outerBalancingHash.getOrElse(super.balancingHash)
    }.createGroups
      .createSeedHashes
      .createSeedNodes(keepOrder = true)
      .createNode

    v1.copy(chainer = Some(eventLogChainer))

  }
}

@Singleton
class TreeEventLogCreation @Inject() (config: Config)(implicit ec: ExecutionContext)
  extends Executor[Future[ChainerPipeData], Future[ChainerPipeData]]
  with ProducerConfPaths
  with LazyLogging {

  def modeFromConfig: String = config.getString("eventLog.mode")
  def mode: Mode = Mode.getMode(modeFromConfig)

  logger.info("Tree EventLog Creator Mode: [{}]", mode.value)

  override def apply(v1: Future[ChainerPipeData]): Future[ChainerPipeData] = {

    v1.map { v1 =>

      val chainerEventLog = v1.chainer
        .flatMap { x => x.getNode.map(rn => (rn, x.es)) }
        .map { case (node, els) =>

          val rootHash = node.value

          Try(EventLogJsonSupport.ToJson(node).get).map {

            logger.debug(s"New [${mode.value}] tree(${els.size}) created, root hash is: $rootHash")

            val category = mode.category
            val serviceClass = mode.serviceClass
            val lookupName = mode.lookupName
            val customerId = mode.customerId

            EventLog(_)
              .withNewId(rootHash)
              .withCategory(category)
              .withCustomerId(customerId)
              .withServiceClass(serviceClass)
              .withRandomNonce
              .addLookupKeys(
                LookupKey(
                  lookupName,
                  category,
                  (rootHash, category),
                  els.map(x => (x.id, x.category))
                )
              )
              .addOriginHeader(category)
              .addTraceHeader(mode.value)
              .sign(config)

          }
        }
        .getOrElse {
          Failure(TreeEventLogCreationException(s"Error creating EventLog from [${mode.value}] Chainer", v1))
        }

      chainerEventLog match {
        case Success(value) =>
          v1.copy(treeEventLog = Some(value))
        case Failure(e) =>
          logger.error(s"Error creating EventLog from [${mode.value}] (2): " + e.getMessage)
          throw TreeEventLogCreationException(e.getMessage, v1)

      }

    }

  }
}

@Singleton
class CreateProducerRecords @Inject() (
    config: Config,
    @Named(DefaultTreeCounter.name) counter: Counter
)(implicit ec: ExecutionContext)
  extends Executor[Future[ChainerPipeData], Future[ChainerPipeData]]
  with ProducerConfPaths
  with LazyLogging {

  override def apply(v1: Future[ChainerPipeData]): Future[ChainerPipeData] = {

    v1.map { v1 =>

      try {

        val topic = config.getStringAsOption(TOPIC_PATH).getOrElse("com.ubirch.eventlog")

        lazy val treeEventLogProducerRecord = v1.treeEventLog.map { treeEl =>
          counter.counter.labels(treeEl.category).inc()
          Go(ProducerRecordHelper.toRecordFromEventLog(topic, v1.id.toString, treeEl))
        }.toVector

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
class Commit @Inject() (basicCommitter: BasicCommit, metricsLoggerBasic: MetricsLoggerBasic)(implicit ec: ExecutionContext)
  extends Executor[Future[ChainerPipeData], Future[ChainerPipeData]] {

  override def apply(v1: Future[ChainerPipeData]): Future[ChainerPipeData] = {

    val futureMetadata = v1.map(_.producerRecords)
      .flatMap { prs =>
        Future.sequence {
          prs.map { x =>
            val futureResp = basicCommitter(x)
            futureResp.map {
              case Some(_) => metricsLoggerBasic.incSuccess
              case None => metricsLoggerBasic.incFailure
            }
            futureResp
          }
        }
      }.map { x =>
        x.flatMap(_.toVector)
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
