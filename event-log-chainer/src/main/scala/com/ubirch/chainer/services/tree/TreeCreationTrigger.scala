package com.ubirch.chainer.services.tree

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.chainer.services.InstantMonitor

import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.joda.time.Instant

/**
  * Represents an object that allows to trigger the creation of a tree.
  * @param instantMonitor Represents a monitor of instances.
  * @param config Represents the configuration object
  */
@Singleton
class TreeCreationTrigger @Inject() (
    instantMonitor: InstantMonitor,
    config: Config
) extends LazyLogging {

  val minTreeRecords: Int = config.getInt(TreePaths.MIN_TREE_RECORDS)
  val every: Int = config.getInt(TreePaths.TREE_EVERY)

  logger.info("Min Tree Records [{}] every [{}] seconds", minTreeRecords, every)

  def goodToCreate(consumerRecords: Vector[ConsumerRecord[String, String]]): Boolean = {
    val currentRecordsSize = consumerRecords.size
    lazy val currentElapsedSeconds = instantMonitor.elapsedSeconds
    currentRecordsSize >= minTreeRecords || currentElapsedSeconds >= every
  }

  def lastTree: Instant = instantMonitor.lastInstant

  def elapsedSeconds: Long = instantMonitor.elapsedSeconds

  def registerNewTreeInstant: Instant = instantMonitor.registerNewInstant

}
