package com.ubirch.chainer.services.tree

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.chainer.services.InstantMonitor
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.concurrent.ExecutionContext

@Singleton
class TreeUpgrade @Inject() (
    instantMonitor: InstantMonitor,
    config: Config
)(implicit ec: ExecutionContext) extends LazyLogging {

  val every: Int = 30 //config.getInt("eventLog.treeEvery")

  logger.info("Tree Upgrade every [{}] seconds", every)

  def goodToUpgrade = instantMonitor.elapsedSeconds >= every

  def lastUpgrade = instantMonitor.lastInstant

  def elapsedSeconds = instantMonitor.elapsedSeconds

  def registerNewUpgrade = instantMonitor.registerNewInstant

}
