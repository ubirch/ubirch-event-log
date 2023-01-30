package com.ubirch.util

import java.util.UUID

import com.datastax.oss.driver.api.core.uuid.Uuids

/**
  * UUID helper. It contains
  * - Random
  * - Time-based
  * UUID generators.
  */
trait UUIDHelper {

  def randomUUID = UUID.randomUUID()

  def timeBasedUUID = Uuids.timeBased()

}

/**
  * Util helper to use the UUID useful functions without extending it or
  * mixing it.
  */
object UUIDHelper extends UUIDHelper

