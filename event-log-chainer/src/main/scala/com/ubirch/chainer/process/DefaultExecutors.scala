package com.ubirch.chainer.process

import java.util.UUID

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.{ ConsumerConfPaths, ProducerConfPaths }
import com.ubirch.chainer.services.kafka.consumer.ChainerPipeData
import com.ubirch.chainer.services.tree.{ TreeMonitor, TreePaths }
import com.ubirch.chainer.util._
import com.ubirch.kafka.util.Exceptions.NeedForPauseException
import com.ubirch.models.EnrichedEventLog.enrichedEventLog
import com.ubirch.models.{ Error, EventLog }
import com.ubirch.process.Executor
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.util.Implicits.enrichedConfig
import com.ubirch.util._
import javax.inject._

import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps
import scala.util.{ Failure, Success, Try }

/**
  * Represents an executor for filtering and triggering chaining processes
  * @param treeMonitor Represents a service for monitoring the periodicity for the creation of trees
  * @param config Represents a config object
  * @param ec Represents the asynchronous processing object.
  */
@Singleton
class FilterEmpty @Inject() (treeMonitor: TreeMonitor, config: Config)(implicit ec: ExecutionContext)
  extends Executor[Vector[ConsumerRecord[String, String]], Future[ChainerPipeData]]
  with LazyLogging {

  val pause: Int = config.getInt(TreePaths.TREE_EVERY)

  logger.info("Pause millis [{}]", pause)

  override def apply(v1: Vector[ConsumerRecord[String, String]]): Future[ChainerPipeData] = Future {
    val records = v1.filter(_.value().nonEmpty)
    lazy val pd = ChainerPipeData(records, Vector.empty, Vector.empty, Vector.empty, Vector.empty)

    if (pause > 0) {
      if (records.nonEmpty) {
        if (treeMonitor.goodToCreate(records)) {
          logger.debug("The chainer threshold HAS been reached")
          pd
        } else {
          logger.debug("The chainer threshold HASN'T been reached")
          throw NeedForPauseException("Unreached Threshold", "The chainer threshold hasn't been reached yet", Some(pause millis))
        }

      } else {
        logger.error("No Records Found")
        throw EmptyValueException("No Records Found", pd)
      }
    } else {
      logger.error(s"Wrong params: [$pause] pause millis")
      throw WrongParamsException(s"Wrong params: [$pause] pause millis", pd)
    }
  }

}

/**
  * Represents an EventLog Parser
  * @param reporter Represents a helper for error reporting
  * @param ec Represents the asynchronous processing object.
  */
@Singleton
class EventLogsParser @Inject() (reporter: Reporter)(implicit ec: ExecutionContext)
  extends Executor[Future[ChainerPipeData], Future[ChainerPipeData]]
  with LazyLogging {

  import reporter.Types._

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

  def uuid: UUID = UUIDHelper.timeBasedUUID

}

/**
  * Represents an Eventlog signer for eventlogs created. Merkle trees are packaged into Eventlogs.
  * @param reporter Represents a helper for error reporting
  * @param config Represents a config object
  * @param ec Represents the asynchronous processing object.
  */
@Singleton
class EventLogsSigner @Inject() (reporter: Reporter, config: Config)(implicit ec: ExecutionContext)
  extends Executor[Future[ChainerPipeData], Future[ChainerPipeData]]
  with LazyLogging {

  import reporter.Types._

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

  def uuid: UUID = UUIDHelper.timeBasedUUID
}

/**
  * Represents an Executor to create Trees
  * @param treeMonitor Represents a service for monitoring the periodicity for the creation of trees
  * @param ec Represents the asynchronous processing object.
  */
class TreeCreatorExecutor @Inject() (treeMonitor: TreeMonitor)(implicit ec: ExecutionContext)
  extends Executor[Future[ChainerPipeData], Future[ChainerPipeData]]
  with LazyLogging {

  override def apply(v1: Future[ChainerPipeData]): Future[ChainerPipeData] = v1.map { v1 =>

    try {
      val chainers = treeMonitor.createTrees(v1.eventLogs.toList).toVector
      val eventLogTrees = treeMonitor.createEventLogs(chainers, treeMonitor.headersNormalCreation)
      v1.copy(chainers = chainers, treeEventLogs = eventLogTrees)
    } catch {
      case e: Exception =>
        throw TreeCreatorExecutorException("Error Creating Tree: " + e.getMessage, v1)
    }
  }
}

/**
  * Represents a kafka committer
  * @param monitor Represents a service for monitoring the periodicity for the creation of trees
  * @param config Represents a config object
  * @param ec Represents the asynchronous processing object.
  */
@Singleton
class Commit @Inject() (monitor: TreeMonitor, config: Config)(implicit ec: ExecutionContext)
  extends Executor[Future[ChainerPipeData], Future[ChainerPipeData]] with ProducerConfPaths with LazyLogging {

  lazy val metricsSubNamespace: String = config.getString(ConsumerConfPaths.METRICS_SUB_NAMESPACE)

  val topic = config.getStringAsOption(TOPIC_PATH).getOrElse("com.ubirch.eventlog")

  override def apply(v1: Future[ChainerPipeData]): Future[ChainerPipeData] = {

    val futureMetadata = v1.map(_.treeEventLogs)
      .flatMap { prs =>
        Future.sequence {
          prs.map { el =>
            monitor.publishWithCache(topic, el)
          }
        }
      }

    val futureResp = for {
      mds <- futureMetadata
      v <- v1
    } yield {
      v.copy(recordsMetadata = mds)
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

  def eventLogSigner: EventLogsSigner

  def commit: Commit

}

/**
  * Simple Aggregated Family of Executors
  * @param filterEmpty Represents an executor for filtering and triggering chaining processes
  * @param eventLogParser Represents an EventLog Parser.
  * @param treeCreatorExecutor Represents an Executor to create Trees
  * @param eventLogSigner Represents an Eventlog signer for eventlogs created. Merkle trees are packaged into Eventlogs.
  * @param commit Represents a kafka committer
  */
@Singleton
class DefaultExecutorFamily @Inject() (
    val filterEmpty: FilterEmpty,
    val eventLogParser: EventLogsParser,
    val treeCreatorExecutor: TreeCreatorExecutor,
    val eventLogSigner: EventLogsSigner,
    val commit: Commit
) extends ExecutorFamily
