package com.ubirch.chainer.process

import java.util.UUID

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ProducerConfPaths
import com.ubirch.chainer.ServiceTraits
import com.ubirch.chainer.models.Chainer
import com.ubirch.chainer.services.kafka.consumer.ChainerPipeData
import com.ubirch.chainer.util._
import com.ubirch.kafka.producer.StringProducer
import com.ubirch.models.EnrichedEventLog.enrichedEventLog
import com.ubirch.models.{ Error, EventLog, LookupKey }
import com.ubirch.process.Executor
import com.ubirch.services.kafka.producer.Reporter
import com.ubirch.util.Implicits.enrichedConfig
import com.ubirch.util._
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.{ ProducerRecord, RecordMetadata }

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success, Try }

class FilterEmpty @Inject() (implicit ec: ExecutionContext)
  extends Executor[Vector[ConsumerRecord[String, String]], Future[ChainerPipeData]]
  with LazyLogging {

  override def apply(v1: Vector[ConsumerRecord[String, String]]): Future[ChainerPipeData] = Future {
    val records = v1.filter(_.value().nonEmpty)
    lazy val pd = ChainerPipeData(records, Vector.empty, None, None, Vector.empty, Vector.empty)
    if (records.nonEmpty) {
      pd
    } else {
      logger.error("No Records Found")
      throw EmptyValueException("No Records Found", pd)
    }
  }

}

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

class TreeEventLogCreation @Inject() (config: Config)(implicit ec: ExecutionContext)
  extends Executor[Future[ChainerPipeData], Future[ChainerPipeData]]
  with ProducerConfPaths
  with LazyLogging {

  override def apply(v1: Future[ChainerPipeData]): Future[ChainerPipeData] = {

    v1.map { v1 =>

      val chainerEventLog = v1.chainer.flatMap { _.getNode }
        .map { node =>

          Try(EventLogJsonSupport.ToJson(node).get).map {
            logger.debug(s"new chainer tree created, root hash is: ${node.value}")
            EventLog(_)
              .withCategory(ServiceTraits.SLAVE_TREE_CATEGORY)
              .withCustomerId("ubirch")
              .withServiceClass("ubirchChainerSlave")
              .withNewId(node.value)
              .withRandomNonce
              .sign(config)
          }
        }
        .getOrElse {
          Failure(TreeEventLogCreationException("Error creating EventLog from Chainer", v1))
        }

      chainerEventLog match {
        case Success(value) =>
          v1.copy(treeEventLog = Some(value))
        case Failure(e) =>
          logger.error("Error creating EventLog from Chainer (2): " + e.getMessage)
          throw TreeEventLogCreationException(e.getMessage, v1)

      }

    }

  }
}

class CreateProducerRecords @Inject() (config: Config)(implicit ec: ExecutionContext)
  extends Executor[Future[ChainerPipeData], Future[ChainerPipeData]]
  with ProducerConfPaths
  with LazyLogging {

  override def apply(v1: Future[ChainerPipeData]): Future[ChainerPipeData] = {

    v1.map { v1 =>

      try {

        val topic = config.getStringAsOption(TOPIC_PATH).getOrElse("com.ubirch.eventlog")

        val treeEventLogId = v1.treeEventLog.map(_.id)

        val lookupKey = for {
          treeId <- treeEventLogId
          ch <- v1.chainer
        } yield {
          val values = ch.es.map(_.id)
          LookupKey(LookupKey.SLAVE_TREE_ID, LookupKey.SLAVE_TREE, treeId, values)
        }

        lazy val treeEventLogProducerRecord = v1.treeEventLog.map { x =>
          val eventLogWithHeaders = {
            x.addOriginHeader(ServiceTraits.SLAVE_TREE_CATEGORY)
              .addLookupKeys(lookupKey.map(x => Seq(x)).getOrElse(Nil): _*)
          }

          Go(ProducerRecordHelper.toRecordFromEventLog(topic, v1.id.toString, eventLogWithHeaders))
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

class Commit @Inject() (basicCommitter: BasicCommit)(implicit ec: ExecutionContext)
  extends Executor[Future[ChainerPipeData], Future[ChainerPipeData]] {

  override def apply(v1: Future[ChainerPipeData]): Future[ChainerPipeData] = {

    val futureMetadata = v1.map(_.producerRecords)
      .flatMap { prs =>
        Future.sequence {
          prs.map(x => basicCommitter(x))
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

class BasicCommit @Inject() (stringProducer: StringProducer, config: Config)(implicit ec: ExecutionContext)
  extends Executor[Decision[ProducerRecord[String, String]], Future[Option[RecordMetadata]]] {

  val futureHelper = new FutureHelper()

  def commit(value: Decision[ProducerRecord[String, String]]): Future[Option[RecordMetadata]] = {
    value match {
      case Go(record) =>
        val javaFuture = stringProducer.getProducerOrCreate.send(record)
        futureHelper.fromJavaFuture(javaFuture).map(x => Option(x))
      case Ignore() =>
        Future.successful(None)
    }
  }

  override def apply(v1: Decision[ProducerRecord[String, String]]): Future[Option[RecordMetadata]] = {

    try {
      commit(v1)
    } catch {
      case e: Exception =>
        Future.failed(BasicCommitException(e.getMessage))
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
