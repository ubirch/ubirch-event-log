package com.ubirch.chainer.services.tree

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.chainer.models.Chainer
import com.ubirch.models.EventLog
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class TreeMonitor @Inject() (
    treeCache: TreeCache,
    treeCreator: TreeCreator,
    treeEventLogCreator: TreeEventLogCreator,
    treePublisher: TreePublisher,
    val treeCreationTrigger: TreeCreationTrigger)(implicit ec: ExecutionContext) extends LazyLogging {

  implicit val scheduler = monix.execution.Scheduler(ec)

  def start = {
    scheduler.scheduleWithFixedDelay(0.seconds, 1.second) {
      logger.info("Last incoming tree was at {} with current elapsed seconds {} ",  treeCreationTrigger.lastTree, treeCreationTrigger.elapsedSeconds)
      treeCache.latestTree.onComplete{
        case Success(Some(value)) =>
          logger.info("Last tree is {}", value.toJson)
        case Success(None) =>
          logger.info("No tree found")
        case Failure(exception) =>
          logger.error("Error getting last tree {}" + exception.getMessage)
      }
    }
  }

  def goodToCreate(consumerRecords: Vector[ConsumerRecord[String, String]]) = {
    val good = treeCreationTrigger.goodToCreate(consumerRecords)
    if(good) {
      logger.info("Tree creation is OK to go")
      treeCreationTrigger.registerNewTreeInstant
    } else
      logger.info("Tree creation is not ready yet")

    good
  }

  def createTrees(eventLogs: List[EventLog]) = {

    for {
      maybeLatest <- treeCache.latestHash
      (chainers, latest) <- Future(treeCreator.create(eventLogs, maybeLatest)(treeCache.prefix))
      _ <- treeCache.setLatestHash(latest)
    } yield {
      chainers
    }

  }

  def createEventLogs(chainers: Vector[Chainer[EventLog]]) = {
    //TODO: WE SHOULD ADD THE NEW HEADERS HERE
    treeEventLogCreator.create(chainers)
  }

  def publish(topic: String, eventLog: EventLog) = {
    for {
      _ <- treeCache.setLatestTree(eventLog)
      p <- treePublisher.publish(topic, eventLog)
    } yield {
      logger.debug("Tree sent to:" + topic)
      p
    }
  }

}
