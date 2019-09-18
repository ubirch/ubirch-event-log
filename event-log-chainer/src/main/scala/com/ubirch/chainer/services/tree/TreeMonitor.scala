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

  require((treeCreationTrigger.every != treeUpgrade.every), "treeEvery and treeUpgrade can't be the same on master mode")

  def treeBillOfMaterialsHook = {
    val lt = treeCreationTrigger.lastTree
    val es = treeCreationTrigger.elapsedSeconds
    logger.info("TreeCR_{}_{}", es, lt)
  }

  def treeUpgradeHook = {
    val lu = treeUpgrade.lastUpgrade
    val es = treeUpgrade.elapsedSeconds
    logger.debug("TreeUP_{}_{}", es, lu)

    if (treeUpgrade.goodToUpgrade) {

      val topic = config.getString(ProducerConfPaths.TOPIC_PATH)
      treeCache.latestTreeEventLog match {
        case Some(value) if mode == Slave =>
          logger.debug("Last FTREE is {}", value.toJson)
          //TODO WE NEED TO ADD HEADERS
          treeUpgrade.registerNewUpgrade
          publishWithNoCache(topic, value)

        case Some(value) if mode == Master =>
          logger.debug("Last RTREE is {}", value.toJson)
          treeUpgrade.registerNewUpgrade
          val topic = config.getString(ProducerConfPaths.TOPIC_PATH)
          publishWithNoCache(topic, value)

        case None if mode == Slave =>
          logger.debug("No FTREE found")
          treeUpgrade.registerNewUpgrade

        case None if mode == Master =>
          logger.debug("No RTREE found ... creating filling ...")
          treeUpgrade.registerNewUpgrade

          val fillingChainers = createTrees(List(
            treeEventLogCreator.createEventLog(
              UUIDHelper.randomUUID.toString,
              JString("caaaugustoss"),
              Nil
            )
          ))

          createEventLogs(fillingChainers.toVector).map { els =>
            publishWithNoCache(topic, els)
          }

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
//      logger.debug("Tree creation is OK to go")
      treeCreationTrigger.registerNewTreeInstant
    } else {
//      logger.debug("Tree creation is not ready yet")
    }

    good
  }

  def createTrees(eventLogs: List[EventLog]) = synchronized {
    val (chainers, latest) = treeCreator.create(eventLogs, treeCache.latestHash)(treeCache.prefix)
    treeCache.setLatestHash(latest)
    chainers
  }

  def createEventLogs(chainers: Vector[Chainer[EventLog]]) = synchronized {
    //TODO: WE SHOULD ADD THE NEW HEADERS HERE
    treeEventLogCreator.create(chainers)
  }

  def publishWithCache(topic: String, eventLog: EventLog) = synchronized {
    logger.debug("Tree sent to:" + topic)
    treeCache.setLatestTree(eventLog)
    treePublisher.publish(topic, eventLog)
  }

  def publishWithNoCache(topic: String, eventLog: EventLog) = synchronized {
    logger.debug("Tree sent to:" + topic)
    treeCache.deleteLatestTree
    treePublisher.publish(topic, eventLog)
  }

}
