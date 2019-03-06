package com.ubirch.util

import java.util.concurrent.atomic.AtomicInteger

import com.typesafe.scalalogging.Logger
import org.slf4j.LoggerFactory

/**
  * Logging helper to add a version number to the instance upon logging.
  */
trait VersionedLazyLogging {

  @transient
  val version: AtomicInteger

  @transient
  protected lazy val logger: Logger =
    Logger(LoggerFactory.getLogger(getClass.getName + "-" + version.getAndAdd(1)))

}
