package com.ubirch.chainer.services.tree

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ProducerConfPaths
import com.ubirch.chainer.models.{ Chainer, Master, Mode, Slave }
import com.ubirch.models.{ EventLog, HeaderNames }
import com.ubirch.util.UUIDHelper
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.json4s.JsonAST.JString

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

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

  import TreeMonitor._

  val modeFromConfig: String = config.getString("eventLog.mode")

  val mode: Mode = Mode.getMode(modeFromConfig)

  implicit val scheduler = monix.execution.Scheduler(ec)

  def headersNormalCreation = headersNormalCreationFromMode(mode)

  require(treeCreationTrigger.every != treeUpgrade.every, "treeEvery & treeUpgrade can't be the same on master mode")

  def start = {
    scheduler.scheduleWithFixedDelay(0.seconds, 1.second) {
      treeBillOfMaterialsHook
      treeUpgradeHook
    }
  }

  def treeBillOfMaterialsHook: Unit = {
    logger.info("TreeCR_{}_{}", treeCreationTrigger.elapsedSeconds, treeCreationTrigger.lastTree)
  }

  def treeUpgradeHook: Unit = {
    logger.debug("TreeUP_{}_{}", treeUpgrade.elapsedSeconds, treeUpgrade.lastUpgrade)

    if (treeUpgrade.goodToUpgrade) {
      logger.debug("Upgrading Tree 027BE")
      val topic = config.getString(ProducerConfPaths.TOPIC_PATH)
      treeCache
        .latestTreeEventLog
        .map(_.addHeaders(headerExcludeStorage)) match {
          case Some(value) => //For every cached tree that is OK to upgrade, we upgrade.
            logger.debug(s"Last ${mode.value} tree: {}", value.toJson)
            treeUpgrade.registerNewUpgrade
            publishWithNoCache(topic, value)

          case None if mode == Slave =>
            logger.debug("No FTREE found")
            treeUpgrade.registerNewUpgrade

          case None if mode == Master => // If we need to upgrade but no tree found, we create a filling tree
            logger.debug("No RTREE found ... creating filling ...")
            treeUpgrade.registerNewUpgrade

            val fillingChainers = createTrees(List(
              treeEventLogCreator.createEventLog(
                UUIDHelper.randomUUID.toString,
                JString("caaaugustoss"),
                Nil
              )
            ))

            createEventLogs(fillingChainers.toVector).map { el =>
              publishWithNoCache(topic, el)
            }

        }
    }
  }

  def createTrees(eventLogs: List[EventLog]) = synchronized {
    val (chainers, latest) = treeCreator.create(eventLogs, treeCache.latestHash)(treeCache.prefix)
    treeCache.setLatestHash(latest)
    chainers
  }

  def createEventLogs(chainers: Vector[Chainer[EventLog]], headers: (String, String)*) = synchronized {
    treeEventLogCreator.create(chainers).map(_.addHeaders(headers: _*))
  }

  def publishWithNoCache(topic: String, eventLog: EventLog) = synchronized {
    //logger.debug("Tree sent to:" + topic + " " + eventLog.toJson)
    treeCache.deleteLatestTree
    treePublisher.publish(topic, eventLog)
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

  def publishWithCache(topic: String, eventLog: EventLog) = synchronized {
    //logger.debug("Tree sent to:" + topic + " " + eventLog.toJson)
    treeCache.setLatestTree(eventLog)
    treePublisher.publish(topic, eventLog)
  }

}

object TreeMonitor {

  def headersNormalCreationFromMode(mode: Mode) = Mode.foldF(mode)
    .onSlave(() => headerExcludeAggregation)
    .onMaster(() => headerExcludeBlockChain)
    .run

  def headerExcludeBlockChain = HeaderNames.DISPATCHER -> "tags-exclude:blockchain"

  def headerExcludeAggregation = HeaderNames.DISPATCHER -> "tags-exclude:aggregation"

  def headerExcludeStorage = HeaderNames.DISPATCHER -> "tags-exclude:storage"

}
