package com.ubirch.chainer.services.tree

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.{ ConsumerConfPaths, ProducerConfPaths }
import com.ubirch.chainer.models.{ Chainer, CompressedTreeData, Mode, ValueStrategy }
import com.ubirch.chainer.services.metrics.{ DefaultLeavesCounter, DefaultTreeCounter }
import com.ubirch.chainer.util.ChainerJsonSupport
import com.ubirch.models.EnrichedEventLog.enrichedEventLog
import com.ubirch.models.{ EventLog, LookupKey, Value, Values }
import com.ubirch.services.metrics.Counter
import javax.inject._
import org.json4s.JsonAST.JValue

import scala.concurrent.ExecutionContext
import scala.util.{ Failure, Success, Try }

@Singleton
class TreeEventLogCreator @Inject() (
    config: Config,
    @Named(DefaultTreeCounter.name) treeCounter: Counter,
    @Named(DefaultLeavesCounter.name) leavesCounter: Counter
)(implicit ec: ExecutionContext)
  extends ProducerConfPaths
  with LazyLogging {

  import com.ubirch.models.LookupKey._

  lazy val metricsSubNamespace: String = config.getString(ConsumerConfPaths.METRICS_SUB_NAMESPACE)

  def modeFromConfig: String = config.getString("eventLog.mode")
  def mode: Mode = Mode.getMode(modeFromConfig)

  lazy val sign: Boolean = config.getBoolean("eventLog.sign")

  logger.info("Tree EventLog Creator Mode: [{}]", mode.value)

  def upgradeLookups(key: String, value: String) = {
    val category = mode.category
    List(LookupKey(
      Mode.Fold(mode)
        .onSlave(() => Values.SLAVE_TREE_UPGRADE_ID)
        .onMaster(() => Values.MASTER_TREE_UPGRADE_ID)
        .run,
      category + "_UPGRADE",
      key.asKeyWithLabel(category + "_UPGRADE"),
      Mode.Fold(mode)
        .onSlave(() => Seq(Value(value, Option(Values.SLAVE_TREE_CATEGORY), Map.empty)))
        .onMaster(() => Seq(Value(value, Option(Values.MASTER_TREE_CATEGORY), Map.empty)))
        .run
    ))
  }

  def createEventLog(rootHash: String, zero: String, data: JValue, leaves: Seq[EventLog]) = {

    val category = mode.category
    val serviceClass = mode.serviceClass
    val lookupName = mode.lookupName
    val customerId = mode.customerId

    val zeroLookup = if (zero.nonEmpty) {
      List(LookupKey(
        Mode.Fold(mode)
          .onSlave(() => Values.SLAVE_TREE_LINK_ID)
          .onMaster(() => Values.MASTER_TREE_LINK_ID)
          .run,
        category,
        rootHash.asKeyWithLabel(category),
        Mode.Fold(mode)
          .onSlave(() => Seq(Value(zero, Option(Values.SLAVE_TREE_CATEGORY), Map.empty)))
          .onMaster(() => Seq(Value(zero, Option(Values.MASTER_TREE_CATEGORY), Map.empty)))
          .run
      ))
    } else Nil

    val normalTreeLookups = List(LookupKey(
      lookupName,
      category,
      rootHash.asKeyWithLabel(category),
      leaves.flatMap(x => ValueStrategy.getStrategyForNormalLeaves(mode).create(x))
    ))

    val lookupKeys = zeroLookup ++ normalTreeLookups

    val treeEl = EventLog(data)
      .withNewId(rootHash)
      .withCategory(category)
      .withCustomerId(customerId)
      .withServiceClass(serviceClass)
      .withRandomNonce
      .addLookupKeys(lookupKeys: _*)
      .addOriginHeader(category)
      .addTraceHeader(mode.value)

    if (sign) treeEl.sign(config)
    else treeEl
  }

  def createEventLog(chainer: Chainer[EventLog]): Option[EventLog] = {
    for {
      node <- chainer.getNode
      compressedData <- Chainer.compress(chainer)
    } yield {
      val data = ChainerJsonSupport.ToJson[CompressedTreeData](compressedData).get
      createEventLog(node.value, chainer.getZero, data, chainer.seeds)
    }
  }

  def create(chainers: Vector[Chainer[EventLog]]): Vector[EventLog] = {
    chainers
      .map { x =>
        Try(createEventLog(x)) match {
          case Success(Some(tree)) =>
            val leavesSize = x.seeds.size
            logger.info(s"New [${mode.value}] tree($leavesSize) created, root hash is: ${tree.id}")
            treeCounter.counter.labels(metricsSubNamespace, tree.category).inc()
            leavesCounter.counter.labels(metricsSubNamespace, tree.category + "_LEAVES").inc(leavesSize)
            tree
          case Success(None) =>
            logger.error(s"Error creating EventLog from [${mode.value}] (2) ")
            throw new Exception("Error creating EventLog")
          case Failure(e) =>
            logger.error(s"Error creating EventLog from [${mode.value}] (3): ", e)
            throw e
        }
      }
  }
}
