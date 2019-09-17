package com.ubirch.chainer.services

import java.util.concurrent.atomic.AtomicReference

import com.ubirch.util.Implicits.enrichedInstant
import javax.inject._
import org.joda.time.Instant

trait InstantMonitor {
  def lastInstant: Instant
  def registerNewInstant: Instant
  def elapsedSeconds: Long
}

class AtomicInstantMonitor extends InstantMonitor {

  private val lastChainingInstant: AtomicReference[Instant] = new AtomicReference[Instant](new Instant())

  override def lastInstant: Instant = lastChainingInstant.get()

  override def registerNewInstant: Instant = {
    lastChainingInstant.set(new Instant())
    lastInstant
  }

  override def elapsedSeconds: Long = lastInstant.secondsBetween(new Instant())
}
