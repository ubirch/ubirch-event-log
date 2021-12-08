package com.ubirch.chainer.services.tree

import java.util.concurrent.atomic.AtomicReference

import com.typesafe.config.Config
import com.ubirch.chainer.models.Mode
import com.ubirch.models.EventLog
import javax.inject._

/**
  * Represents a cache for trees. It is designed to store the latest tree.
  * @param config Represents the configuration object
  */
@Singleton
class TreeCache @Inject() (config: Config) {

  private val modeFromConfig: String = config.getString(TreePaths.MODE)

  private val mode: Mode = Mode.getMode(modeFromConfig)

  private val latestHash = new AtomicReference[Option[String]](None)

  private val latestTreeEventLog = new AtomicReference[Option[EventLog]](None)

  def getLatestHash: Option[String] = latestHash.get()

  def setLatestHash(value: String): Unit = {
    require(value.nonEmpty, "latest hash can't be empty")
    latestHash.set(Some(prefix(value)))
  }

  def withPrefix = false

  def prefix(value: String): String = {
    if (withPrefix) {
      val px = Mode.fold(mode)(() => "sl.")(() => "ml.")
      if (!value.startsWith(px)) px + value else value
    } else
      value
  }

  def getLatestTreeEventLog: Option[EventLog] = latestTreeEventLog.get()

  def setLatestTree(eventLog: EventLog): Unit = latestTreeEventLog.set(Option(eventLog))

  def resetLatestTree: Unit = latestTreeEventLog.set(None)

}
