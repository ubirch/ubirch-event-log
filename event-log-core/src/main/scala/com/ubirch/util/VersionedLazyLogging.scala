package com.ubirch.util

import java.util.concurrent.atomic.AtomicInteger

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

trait VersionedLazyLogging {

  @transient
  val version: AtomicInteger

  @transient
  protected lazy val logger: Logger =
    Logger(LoggerFactory.getLogger(getClass.getName + "-" + version.getAndAdd(1)))

}
