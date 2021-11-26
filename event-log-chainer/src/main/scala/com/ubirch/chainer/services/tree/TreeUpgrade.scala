package com.ubirch.chainer.services.tree

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.chainer.services.InstantMonitor

import org.joda.time.Instant

import javax.inject._

/**
  * Represents a components that controls the tree upgrade process
  * @param instantMonitor Represents a component for controlling instants or tick for the tree creation
  * @param config Represents the configuration object
  */
@Singleton
class TreeUpgrade @Inject() (
    instantMonitor: InstantMonitor,
    config: Config
) extends LazyLogging {

  val every: Int = config.getInt(TreePaths.TREE_UPGRADE)

  logger.info("Tree Upgrade every [{}] seconds", every)

  def goodToUpgrade: Boolean = instantMonitor.elapsedSeconds >= every

  def lastUpgrade: Instant = instantMonitor.lastInstant

  def elapsedSeconds: Long = instantMonitor.elapsedSeconds

  def registerNewUpgrade: Instant = instantMonitor.registerNewInstant

}
