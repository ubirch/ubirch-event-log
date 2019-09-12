package com.ubirch.chainer.services

import com.typesafe.config.Config
import com.ubirch.chainer.models.Mode
import com.ubirch.models.{Cache, MemCache}
import javax.inject._

import scala.concurrent.{ExecutionContext, Future}

class TreeCache @Inject() (@Named(MemCache.name) cache: Cache, config: Config)(implicit ec: ExecutionContext) {

  val modeFromConfig: String = config.getString("eventLog.mode")

  val mode: Mode = Mode.getMode(modeFromConfig)

  val LATEST_TREE: String = Mode.fold(mode)("LASTEST_SLAVE_TREE")( "LATEST_MASTER_TREE")

  def prefix(value: String): String = {
    val px = Mode.fold(mode)("sl.")(("ml."))
    if(!value.startsWith(px)) px + value else value
  }

  def latest: Future[Option[String]] = cache.get(LATEST_TREE)

  def setLatest(value: String): Future[Option[String]] = {
    cache.put(LATEST_TREE, prefix(value))
  }

}
