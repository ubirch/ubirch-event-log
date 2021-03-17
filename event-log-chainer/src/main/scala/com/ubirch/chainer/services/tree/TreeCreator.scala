package com.ubirch.chainer.services.tree

import com.typesafe.config.Config
import com.ubirch.chainer.models.Chainer
import com.ubirch.chainer.models.Chainer.CreateConfig
import com.ubirch.models.EventLog
import javax.inject._

import com.ubirch.chainer.util.Hasher

import scala.concurrent.ExecutionContext

/**
  * Represents a creator of trees
  * @param config Represents the configuration object
  * @param ec Represents an execution context for this object
  */
class TreeCreator @Inject() (config: Config)(implicit ec: ExecutionContext) {

  import com.ubirch.chainer.models.Chainables.eventLogChainable

  lazy val splitTrees: Boolean = config.getBoolean("eventLog.split")
  lazy val splitSize: Int = config.getInt("eventLog.splitSize")

  def create(eventLogs: List[EventLog], maybeInitialTreeHash: Option[String])(prefixer: String => String): (List[Chainer[EventLog, String, String]], Option[String]) = {
    val config = CreateConfig[String](
      maybeInitialTreeHash = maybeInitialTreeHash,
      split = splitTrees,
      splitSize = splitSize,
      prefixer = prefixer,
      merger = Hasher.mergeAndHash,
      balancer = _ => outerBalancingHash.getOrElse(Chainer.getEmptyNodeVal)
    )
    Chainer.create(eventLogs, config)
  }

  def outerBalancingHash: Option[String] = None

}
