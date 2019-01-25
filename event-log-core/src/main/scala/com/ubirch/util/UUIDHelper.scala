package com.ubirch.util

import java.util.UUID

import com.datastax.driver.core.utils.UUIDs

trait UUIDHelper {

  def randomUUID = UUID.randomUUID()

  def timeBasedUUID = UUIDs.timeBased()

}

object UUIDHelper extends UUIDHelper

