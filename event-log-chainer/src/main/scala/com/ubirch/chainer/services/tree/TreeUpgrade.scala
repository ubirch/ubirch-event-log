package com.ubirch.chainer.services.tree

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.chainer.services.InstantMonitor
import javax.inject._

import scala.concurrent.ExecutionContext

/**
  * Represents a components that controls the tree upgrade process
  * @param instantMonitor Represents a component for controlling instants or tick for the tree creation
  * @param config Represents the configuration object
  * @param ec Represents an execution context for this object
  */
@Singleton
class TreeUpgrade @Inject() (
    instantMonitor: InstantMonitor,
    config: Config
)(implicit ec: ExecutionContext) extends LazyLogging {

  val every: Int = config.getInt(TreePaths.TREE_UPGRADE)

  logger.info("Tree Upgrade every [{}] seconds", every)

  def goodToUpgrade = instantMonitor.elapsedSeconds >= every

  def lastUpgrade = instantMonitor.lastInstant

  def elapsedSeconds = instantMonitor.elapsedSeconds

  def registerNewUpgrade = instantMonitor.registerNewInstant

}
