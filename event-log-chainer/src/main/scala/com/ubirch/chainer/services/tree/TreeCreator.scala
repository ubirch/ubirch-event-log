package com.ubirch.chainer.services.tree

import com.typesafe.config.Config
import com.ubirch.chainer.models.Chainer
import com.ubirch.models.EventLog
import javax.inject._

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext

class TreeCreator @Inject() (config: Config)(implicit ec: ExecutionContext) {

  import com.ubirch.chainer.models.Chainables.eventLogChainable

  lazy val splitTrees: Boolean = config.getBoolean("eventLog.split")

  def outerBalancingHash: Option[String] = None

  def split(eventLogs: List[EventLog]): List[List[EventLog]] = {
    if (splitTrees && eventLogs.size >= 100)
      eventLogs.sliding(50, 50).toList
    else
      Iterator(eventLogs).toList
  }

  def create(eventLogs: List[EventLog], maybeInitialTreeHash: Option[String])(prefixer: String => String) = {

    @tailrec def go(
        splits: List[List[EventLog]],
        chainers: List[Chainer[EventLog]],
        latestHash: String
    ): (List[Chainer[EventLog]], String) = {
      splits match {
        case Nil => (chainers, latestHash)
        case xs :: xss =>
          val chainer = new Chainer(xs) {
            override def balancingHash: String = outerBalancingHash.getOrElse(super.balancingHash)
          }
            .withHashZero(latestHash)
            .withGeneralGrouping
            .createSeedHashes
            .createSeedNodes(keepOrder = true)
            .createNode

          go(xss, chainers ++ List(chainer), chainer.getNode.map(x => prefixer(x.value)).getOrElse(""))

      }
    }

    val splits = split(eventLogs)
    go(splits, Nil, maybeInitialTreeHash.getOrElse(""))

  }

}
