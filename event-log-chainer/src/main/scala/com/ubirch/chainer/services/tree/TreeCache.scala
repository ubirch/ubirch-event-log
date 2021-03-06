package com.ubirch.chainer.services.tree

import java.util.concurrent.atomic.AtomicReference

import com.typesafe.config.Config
import com.ubirch.chainer.models.Mode
import com.ubirch.models.EventLog
import javax.inject._

import scala.concurrent.ExecutionContext

/**
  * Represents a cache for trees. It is designed to store the latest tree.
  * @param config Represents the configuration object
  * @param ec Represents an execution context for this object
  */
@Singleton
class TreeCache @Inject() (config: Config)(implicit ec: ExecutionContext) {

  val modeFromConfig: String = config.getString("eventLog.mode")

  val mode: Mode = Mode.getMode(modeFromConfig)

  private val _latestHash = new AtomicReference[Option[String]](None)

  private val _latestTreeEventLog = new AtomicReference[Option[EventLog]](None)

  def latestHash: Option[String] = _latestHash.get()

  def setLatestHash(value: String) = _latestHash.set(Some(prefix(value)))

  def prefix(value: String): String = {
    if (false) {
      val px = Mode.fold(mode)(() => "sl.")(() => "ml.")
      if (!value.startsWith(px)) px + value else value
    } else
      value
  }

  def latestTreeEventLog: Option[EventLog] = _latestTreeEventLog.get()

  def setLatestTree(eventLog: EventLog) = _latestTreeEventLog.set(Option(eventLog))

  def deleteLatestTree = _latestTreeEventLog.set(None)

}
