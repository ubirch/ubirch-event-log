package com.ubirch.chainer.services.tree

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ProducerConfPaths
import com.ubirch.chainer.models.{ Chainer, Master, Mode, Slave }
import com.ubirch.kafka.util.FutureHelper
import com.ubirch.models.EnrichedEventLog._
import com.ubirch.models.{ EventLog, HeaderNames, TagExclusions }
import com.ubirch.util.UUIDHelper

import monix.execution.{ Cancelable, Scheduler }

import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.json4s.JsonAST.JString

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps

/**
  * Represents a controller/façade that controls the pipeline and the tree creation
  * @param treeWarmUp Represents a component for warming up the system
  * @param treeCache Represents a component for caching trees
  * @param treeCreator Represents a component for creating trees
  * @param treeEventLogCreator Represents a component for creating event log out of trees
  * @param treePublisher Represents a component for sending to kafka
  * @param treeCreationTrigger Represents a component that say when it is OK to create a tree
  * @param treeUpgrade Represents a components that controls the tree upgrade process
  * @param config Represents the configuration object
  * @param ec Represents an execution context for this object
  */
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

  val modeFromConfig: String = config.getString(TreePaths.MODE)

  val mode: Mode = Mode.getMode(modeFromConfig)

  implicit val scheduler: Scheduler = monix.execution.Scheduler(ec)

  def headersNormalCreation: (String, String) = headersNormalCreationFromMode(mode)

  require(treeCreationTrigger.every != treeUpgrade.every, "treeEvery & treeUpgrade can't be the same on master mode")

  def start: Cancelable = {

    def tick = {
      scheduler.scheduleWithFixedDelay(0.seconds, 1.second) {
        treeBillOfMaterialsHook()
        treeUpgradeHook(forceUpgrade = false)
      }
    }

    val doWarmup = Mode
      .foldF(mode)
      .onSlave { () =>
        logger.info(s"Tree(${mode.value}) Warm-up succeeded.")
        Future.successful(tick)
      }
      .onMaster { () =>
        treeWarmUp.warmup.map {
          case AllGood =>
            logger.info(s"Tree(${mode.value}) Warm-up succeeded.")
            tick
          case WhatTheHeck =>
            logger.error(s"Tree(${mode.value}) Warm-up failed.")
            throw new Exception(s"Tree(${mode.value}) Warm-up failed.")
          case CreateGenesisTree =>
            treeUpgradeHook(forceUpgrade = true)
            tick
        }
      }
      .run

    FutureHelper.await(doWarmup, 10 seconds)

  }

  def treeBillOfMaterialsHook(): Unit =
    logger.debug("TreeCR_{}_{}", treeCreationTrigger.elapsedSeconds, treeCreationTrigger.lastTree)

  def treeUpgradeHook(forceUpgrade: Boolean): Unit = {
    logger.debug("TreeUP_{}_{}", treeUpgrade.elapsedSeconds, treeUpgrade.lastUpgrade)

    if (treeUpgrade.goodToUpgrade || forceUpgrade) {
      logger.debug("Upgrading Tree")
      val topic = config.getString(ProducerConfPaths.TOPIC_PATH)
      val latestHash = treeCache.getLatestHash.getOrElse("")
      val latestTree = treeCache.getLatestTreeEventLog

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

  def createAndSendFilling(topic: String, latestHash: String, addBigBangProperties: Boolean): Vector[Future[RecordMetadata]] = {
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
      .map(el => el.addLookupKeys(treeEventLogCreator.upgradeLookups(el.id, latestHash): _*))
      .map { el =>
        if (addBigBangProperties) el.withBigBangTime.addBigBangLookup
        else el
      }
      .map(el => publishWithNoCache(topic, el))
  }

  def createTrees(eventLogs: List[EventLog]): List[Chainer[EventLog, String, String]] = synchronized {
    val (chainers, latest) = treeCreator.create(eventLogs, treeCache.getLatestHash)(treeCache.prefix)
    treeCache.setLatestHash(latest)
    chainers
  }

  def createEventLogs(chainers: Vector[Chainer[EventLog, String, String]], headers: (String, String)*): Vector[EventLog] = synchronized {
    treeEventLogCreator.create(chainers).map(_.addHeaders(headers: _*))
  }

  def publishWithNoCache(topic: String, eventLog: EventLog): Future[RecordMetadata] = synchronized {
    //logger.debug("Tree sent to:" + topic + " " + eventLog.toJson)
    treeCache.resetLatestTree
    treePublisher.publish(topic, eventLog)
  }

  def publishWithCache(topic: String, eventLog: EventLog): Future[RecordMetadata] = synchronized {
    //logger.debug("Tree sent to:" + topic + " " + eventLog.toJson)
    treeCache.setLatestTree(eventLog)
    treePublisher.publish(topic, eventLog)
  }

  def goodToCreate(consumerRecords: Vector[ConsumerRecord[String, String]]): Boolean = {
    val good = treeCreationTrigger.goodToCreate(consumerRecords)
    if (good) treeCreationTrigger.registerNewTreeInstant
    good
  }

}

object TreeMonitor extends TagExclusions {

  def headersNormalCreationFromMode(mode: Mode): (String, String) = Mode.foldF(mode)
    .onSlave(() => headerExcludeAggregation)
    .onMaster(() => headerExcludeBlockChain)
    .run

}
