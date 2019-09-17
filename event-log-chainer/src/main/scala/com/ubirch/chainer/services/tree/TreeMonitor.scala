package com.ubirch.chainer.services.tree

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.chainer.models.Chainer
import com.ubirch.models.EventLog
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TreeMonitor @Inject() (
    treeCache: TreeCache,
    treeCreator: TreeCreator,
    treeEventLogCreator: TreeEventLogCreator,
    treePublisher: TreePublisher,
    val treeCreationTrigger: TreeCreationTrigger)(implicit ec: ExecutionContext) extends LazyLogging {

  implicit val scheduler = monix.execution.Scheduler(ec)

  scheduler.scheduleWithFixedDelay(3.seconds, 5.seconds) {
    println("Fixed delay task")
  }

  def goodToCreate(consumerRecords: Vector[ConsumerRecord[String, String]]) = {
    val good = treeCreationTrigger.goodToCreate(consumerRecords)
    if(good) treeCreationTrigger.registerNewTreeInstant
    good
  }

  def createTrees(eventLogs: List[EventLog]) = {

    for {
      maybeLatest <- treeCache.latest
      (chainers, latest) <- Future(treeCreator.create(eventLogs, maybeLatest)(treeCache.prefix))
      _ <- treeCache.setLatest(latest)
    } yield {
      chainers
    }

  }

  def createEventLogs(chainers: Vector[Chainer[EventLog]]) = {
    //TODO: WE SHOULD ADD THE NEW HEADERS HERE
    treeEventLogCreator.create(chainers)
  }

  def publish(topic: String, eventLog: EventLog) = treePublisher.publish(topic, eventLog)

}
