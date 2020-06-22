package com.ubirch.chainer.services.tree

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.chainer.services.InstantMonitor
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.concurrent.ExecutionContext

/**
  * Represents an object that allows to trigger the creation of a tree.
  * @param instantMonitor Represents a monitor of instances.
  * @param config Represents the configuration object
  * @param ec Represents an execution context for this object
  */
@Singleton
class TreeCreationTrigger @Inject() (
    instantMonitor: InstantMonitor,
    config: Config
)(implicit ec: ExecutionContext) extends LazyLogging {

  val minTreeRecords: Int = config.getInt("eventLog.minTreeRecords")
  val every: Int = config.getInt("eventLog.treeEvery")

  logger.info("Min Tree Records [{}] every [{}] seconds", minTreeRecords, every)

  def goodToCreate(consumerRecords: Vector[ConsumerRecord[String, String]]) = {
    val currentRecordsSize = consumerRecords.size
    lazy val currentElapsedSeconds = instantMonitor.elapsedSeconds
    currentRecordsSize >= minTreeRecords || currentElapsedSeconds >= every
  }

  def lastTree = instantMonitor.lastInstant

  def elapsedSeconds = instantMonitor.elapsedSeconds

  def registerNewTreeInstant = instantMonitor.registerNewInstant

}
