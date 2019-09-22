package com.ubirch.chainer.services.tree

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.{ ConsumerConfPaths, ProducerConfPaths }
import com.ubirch.chainer.models.{ Chainer, Mode, Node, ValueStrategy }
import com.ubirch.chainer.services.metrics.{ DefaultLeavesCounter, DefaultTreeCounter }
import com.ubirch.chainer.util.ChainerJsonSupport
import com.ubirch.models.EnrichedEventLog.enrichedEventLog
import com.ubirch.models.{ EventLog, LookupKey }
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

  def createEventLog(rootHash: String, zero: String, data: JValue, leaves: Seq[EventLog]) = {

    val category = mode.category
    val serviceClass = mode.serviceClass
    val lookupName = mode.lookupName
    val customerId = mode.customerId

    val zeroLookup = if (zero.nonEmpty) {
      List(LookupKey(
        lookupName,
        category,
        rootHash.asKeyWithLabel(category),
        ValueStrategy.getStrategyForZero(mode).create(zero)
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

  def createEventLog(node: Node[String], zero: String, els: Seq[EventLog]): EventLog = {
    val rootHash = node.value
    val data = ChainerJsonSupport.ToJson(node).get
    createEventLog(rootHash, zero, data, els)
  }

  def create(chainers: Vector[Chainer[EventLog]]): Vector[EventLog] = {
    chainers
      .flatMap { x => x.getNode.map(rn => (rn, x.getZero, x.es)) }
      .map { case (node, zero, els) =>
        Try(createEventLog(node, zero, els)) match {
          case Success(tree) =>
            val leavesSize = els.size
            logger.info(s"New [${mode.value}] tree($leavesSize) created, root hash is: ${tree.id}")
            treeCounter.counter.labels(metricsSubNamespace, tree.category).inc()
            leavesCounter.counter.labels(metricsSubNamespace, tree.category + "_LEAVES").inc(leavesSize)
            tree
          case Failure(e) =>
            logger.error(s"Error creating EventLog from [${mode.value}] (2): ", e)
            throw e
        }
      }

  }
}
