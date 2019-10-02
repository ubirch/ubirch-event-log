package com.ubirch.chainer.services.tree

import java.text.SimpleDateFormat

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ProducerConfPaths
import com.ubirch.chainer.models.{ Chainer, Master, Mode, Slave }
import com.ubirch.models.{ EventLog, HeaderNames, LookupKey }
import com.ubirch.util.UUIDHelper
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.json4s.JsonAST.JString

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

@Singleton
class TreeMonitor @Inject() (
    treeWarmUp: TreeWarmUp,
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

    def tick = {
      scheduler.scheduleWithFixedDelay(0.seconds, 1.second) {
        treeBillOfMaterialsHook
        treeUpgradeHook(forceUpgrade = false)
      }
    }

    Mode
      .foldF(mode)
      .onSlave { () =>
        logger.info(s"Tree(${mode.value}) Warm-up succeeded.")
        tick
      }
      .onMaster { () =>
        treeWarmUp.warmup match {
          case AllGood =>
            logger.info(s"Tree(${mode.value}) Warm-up succeeded.")
            tick
          case WhatTheHeck =>
            logger.error(s"Tree(${mode.value} Warm-up failed.")
            throw new Exception(s"Tree(${mode.value}) Warm-up failed.")
          case CreateGenesisTree =>
            treeUpgradeHook(forceUpgrade = true)
            tick
        }
      }
      .run

  }

  def treeBillOfMaterialsHook: Unit =
    logger.debug("TreeCR_{}_{}", treeCreationTrigger.elapsedSeconds, treeCreationTrigger.lastTree)

  def treeUpgradeHook(forceUpgrade: Boolean): Unit = {
    logger.debug("TreeUP_{}_{}", treeUpgrade.elapsedSeconds, treeUpgrade.lastUpgrade)

    if (treeUpgrade.goodToUpgrade || forceUpgrade) {
      logger.debug("Upgrading Tree")
      val topic = config.getString(ProducerConfPaths.TOPIC_PATH)
      val latestHash = treeCache.latestHash.getOrElse("")
      val latestTree = treeCache.latestTreeEventLog

      latestTree
        .map(_.removeHeader(HeaderNames.DISPATCHER))
        .map(x => x.addLookupKeys(treeEventLogCreator.upgradeLookups(x.id, latestHash): _*)) match {

          case Some(value) => //For every cached tree that is OK to upgrade, we upgrade.
            logger.debug(s"Last ${mode.value} tree: {}", value.toJson)
            treeUpgrade.registerNewUpgrade
            publishWithNoCache(topic, value)

          case None if mode == Slave =>
            logger.debug("No FTREE found ... We are good, nothing to do.")
            treeUpgrade.registerNewUpgrade

          case None if mode == Master => // If we need to upgrade but no tree found, we create a filling tree
            treeUpgrade.registerNewUpgrade
            //This is the event-log that will be used to create the tree
            createAndSendFilling(topic, latestHash, addBigBangProperties = forceUpgrade)

        }
    }
  }

  def createAndSendFilling(topic: String, latestHash: String, addBigBangProperties: Boolean) = {
    //This is the event-log that will be used to create the tree
    val fillingEventLog = if (latestHash.isEmpty) {
      logger.debug("No RTREE found as EventLog ... creating filling ...")
      treeEventLogCreator.createEventLog(
        rootHash = "FILLING_LEAF_" + UUIDHelper.randomUUID.toString,
        zero = latestHash,
        data = JString("caaaugustoss"),
        leaves = Nil
      )
    } else {
      logger.debug("No RTREE found as EventLog... creating filling from cache ... [{}]", latestHash)
      treeEventLogCreator.createEventLog(
        rootHash = latestHash,
        zero = "",
        data = JString("caaaugustoss"),
        leaves = Nil
      )
    }

    val fillingChainers = createTrees(List(fillingEventLog))
    //This is the tree event-log (filling)
    createEventLogs(fillingChainers.toVector)
      .map(x => x.addLookupKeys(treeEventLogCreator.upgradeLookups(x.id, latestHash): _*))
      .map { x =>
        if (addBigBangProperties) {
          //Add time zero and lookup
          import LookupKey._
          val sdf = new SimpleDateFormat("dd-M-yyyy hh:mm:ss")
          val newDate = sdf.parse("02-03-1986 00:00:00")
          x.withEventTime(newDate)
            .addLookupKeys(
              LookupKey(
                "big-bang-tree-id",
                "BING_BANG_TREE",
                x.id.asKeyWithLabel("BIG_BANG_TREE"),
                Seq("BIG_BANG_TREE".asValueWithLabel("BIG_BANG_TREE"))
              )
            )
        } else x
      }
      .map(el => publishWithNoCache(topic, el))
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
    logger.debug("Tree sent to:" + topic + " " + eventLog.toJson)
    treeCache.deleteLatestTree
    treePublisher.publish(topic, eventLog)
  }

  def publishWithCache(topic: String, eventLog: EventLog) = synchronized {
    //logger.debug("Tree sent to:" + topic + " " + eventLog.toJson)
    treeCache.setLatestTree(eventLog)
    treePublisher.publish(topic, eventLog)
  }

  def goodToCreate(consumerRecords: Vector[ConsumerRecord[String, String]]) = {
    val good = treeCreationTrigger.goodToCreate(consumerRecords)
    if (good) treeCreationTrigger.registerNewTreeInstant
    good
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
