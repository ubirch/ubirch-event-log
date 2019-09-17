package com.ubirch.chainer.services.tree

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.chainer.services.InstantMonitor
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.concurrent.ExecutionContext

@Singleton
class TreeCreationTrigger @Inject() (
    instantMonitor: InstantMonitor,
    config: Config
)(implicit ec: ExecutionContext) extends LazyLogging {

  val minTreeRecords: Int = config.getInt("eventLog.minTreeRecords")
  val every: Int = config.getInt("eventLog.treeEvery")

  logger.info("Min Tree Records [{}]  every [{}] seconds", minTreeRecords, every)

  def goodToCreate(consumerRecords: Vector[ConsumerRecord[String, String]]) = {
    val currentRecordsSize = consumerRecords.size
    val currentElapsedSeconds = instantMonitor.elapsedSeconds
    val good = currentRecordsSize >= minTreeRecords || currentElapsedSeconds >= every

    if (good) instantMonitor.registerNewInstant

    good
  }

}
