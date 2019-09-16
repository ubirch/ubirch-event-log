package com.ubirch.chainer.services

import com.ubirch.models.EventLog
import javax.inject._

import scala.concurrent.ExecutionContext

@Singleton
class TreeMonitor @Inject() (
    treeCache: TreeCache,
    treeCreator: TreeCreator,
    treeEventLogCreator: TreeEventLogCreator //,
//  treePublisher: TreePublisher
)(implicit ec: ExecutionContext) {

  def create(eventLogs: List[EventLog]) = {
    lazy val futureChainers = treeCache.latest.map { maybeLatest =>
      treeCreator.create(eventLogs, maybeLatest.getOrElse(""))(treeCache.prefix)
    }

    for {
      (chainers, latest) <- futureChainers
      _ <- treeCache.setLatest(latest)
    } yield {
      chainers
    }

  }

}
