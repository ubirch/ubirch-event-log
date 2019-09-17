package com.ubirch.chainer.services.tree

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ProducerConfPaths
import com.ubirch.chainer.models.{ Chainer, Master, Mode, Slave }
import com.ubirch.models.EventLog
import com.ubirch.util.UUIDHelper
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.json4s.JsonAST.JString

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

@Singleton
class TreeMonitor @Inject() (
    treeCache: TreeCache,
    treeCreator: TreeCreator,
    treeEventLogCreator: TreeEventLogCreator,
    treePublisher: TreePublisher,
    val treeCreationTrigger: TreeCreationTrigger,
    treeUpgrade: TreeUpgrade,
    config: Config
)(implicit ec: ExecutionContext) extends LazyLogging {

  implicit val scheduler = monix.execution.Scheduler(ec)

  val modeFromConfig: String = config.getString("eventLog.mode")

  val mode: Mode = Mode.getMode(modeFromConfig)

  def treeBillOfMaterialsHook = {
    logger.info("Tree Creation Trigger {} with current elapsed seconds {} ", treeCreationTrigger.lastTree, treeCreationTrigger.elapsedSeconds)
  }

  def treeUpgradeHook = {
    logger.info("Tree Upgrade {} with current elapsed seconds {} ", treeUpgrade.lastUpgrade, treeUpgrade.elapsedSeconds)
    if (treeUpgrade.goodToUpgrade) {
      val topic = config.getString(ProducerConfPaths.TOPIC_PATH)
      treeCache.latestTree.onComplete {
        case Success(Some(value)) if mode == Slave =>
          logger.info("Last stree is {}", value.toJson)
          //TODO WE NEED TO ADD HEADERS
          treeUpgrade.registerNewUpgrade
          publishWithNoCache(topic, value)

        case Success(None) if mode == Slave =>
          logger.info("No stree found")
          treeUpgrade.registerNewUpgrade

        case Success(Some(value)) if mode == Master =>
          logger.info("Last mtree is {}", value.toJson)
          treeUpgrade.registerNewUpgrade
          val topic = config.getString(ProducerConfPaths.TOPIC_PATH)
          publishWithNoCache(topic, value)

        case Success(None) if mode == Master =>
          logger.info("No mtree found ... creating filling ...")
          treeUpgrade.registerNewUpgrade

          val futureFillingChainer = createTrees(List(
            treeEventLogCreator.createEventLog(
              UUIDHelper.randomUUID.toString,
              JString("caaaugustoss"),
              Nil
            )
          )).map { xs =>
            createEventLogs(xs.toVector)
          }

          futureFillingChainer.map { evs =>
            evs.map { ev =>
              publishWithNoCache(topic, ev)
            }
          }

        case Failure(exception) =>
          logger.error("Error getting last tree {}" + exception.getMessage)
      }
    }
  }

  def start = {
    scheduler.scheduleWithFixedDelay(0.seconds, 1.second) {
      treeBillOfMaterialsHook
      treeUpgradeHook
    }
  }

  def goodToCreate(consumerRecords: Vector[ConsumerRecord[String, String]]) = {
    val good = treeCreationTrigger.goodToCreate(consumerRecords)
    if (good) {
      logger.info("Tree creation is OK to go")
      treeCreationTrigger.registerNewTreeInstant
    } else
      logger.info("Tree creation is not ready yet")

    good
  }

  def createTrees(eventLogs: List[EventLog]) = {

    for {
      maybeLatest <- treeCache.latestHash
      (chainers, latest) <- Future(treeCreator.create(eventLogs, maybeLatest)(treeCache.prefix))
      _ <- treeCache.setLatestHash(latest)
    } yield {
      chainers
    }

  }

  def createEventLogs(chainers: Vector[Chainer[EventLog]]) = {
    //TODO: WE SHOULD ADD THE NEW HEADERS HERE
    treeEventLogCreator.create(chainers)
  }

  def publishWithCache(topic: String, eventLog: EventLog) = {
    for {
      _ <- treeCache.setLatestTree(eventLog)
      p <- treePublisher.publish(topic, eventLog)
    } yield {
      logger.debug("Tree sent to:" + topic)
      p
    }
  }

  def publishWithNoCache(topic: String, eventLog: EventLog) = {
    for {
      _ <- treeCache.deleteLatestTree
      p <- treePublisher.publish(topic, eventLog)
    } yield {
      logger.debug("Tree sent to:" + topic)
      p
    }
  }

}
