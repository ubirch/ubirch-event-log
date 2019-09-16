package com.ubirch.chainer.services

import com.ubirch.chainer.models.Chainer
import com.ubirch.models.EventLog
import javax.inject._

import scala.concurrent.{ ExecutionContext, Future }

class TreeMonitor @Inject() (
    treeCache: TreeCache,
    treeCreator: TreeCreator,
    treeEventLogCreator: TreeEventLogCreator,
    treePublisher: TreePublisher
)(implicit ec: ExecutionContext) {

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

}
