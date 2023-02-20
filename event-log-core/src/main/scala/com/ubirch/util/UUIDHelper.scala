package com.ubirch.util

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs

/**
  * UUID helper. It contains
  * - Random
  * - Time-based
  * UUID generators.
  */
trait UUIDHelper {

  def randomUUID = UUID.randomUUID()

  def timeBasedUUID = UUIDs.timeBased()

}

/**
  * Util helper to use the UUID useful functions without extending it or
  * mixing it.
  */
object UUIDHelper extends UUIDHelper

