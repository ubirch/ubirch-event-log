package com.ubirch.chainer.services.tree

import com.typesafe.config.Config
import com.ubirch.chainer.models.Mode
import com.ubirch.models.{Cache, EventLog, MemCache}
import javax.inject._

import scala.concurrent.{ExecutionContext, Future}

class TreeCache @Inject() (@Named(MemCache.name) cache: Cache, config: Config)(implicit ec: ExecutionContext) {

  val modeFromConfig: String = config.getString("eventLog.mode")

  val mode: Mode = Mode.getMode(modeFromConfig)

  val LATEST_TREE_HASH: String = Mode.fold(mode)("LASTEST_SLAVE_TREE_HASH")("LATEST_MASTER_TREE_HASH")

  val LATEST_TREE: String = Mode.fold(mode)("LASTEST_SLAVE_TREE")("LATEST_MASTER_TREE")

  def prefix(value: String): String = {
    val px = Mode.fold(mode)("sl.")("ml.")
    if (!value.startsWith(px)) px + value else value
  }

  def latestHash: Future[Option[String]] = cache.get(LATEST_TREE_HASH)

  def setLatestHash(value: String): Future[Option[String]] = cache.put(LATEST_TREE_HASH, prefix(value))

  def latestTree: Future[Option[EventLog]] = cache.get(LATEST_TREE)

  def setLatestTree(eventLog: EventLog): Future[Option[EventLog]] = cache.put(LATEST_TREE, eventLog)


}
