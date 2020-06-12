package com.ubirch.chainer.services

import java.util.concurrent.atomic.AtomicReference

import com.ubirch.util.Implicits.enrichedInstant
import org.joda.time.Instant

/**
  * Represents a component for controlling and managing instants
  */
trait InstantMonitor {
  def lastInstant: Instant
  def registerNewInstant: Instant
  def elapsedSeconds: Long
}

/**
  * Represents the implementation for the instant interface throught an atomic reference.
  */
class AtomicInstantMonitor extends InstantMonitor {

  private val lastChainingInstant: AtomicReference[Instant] = new AtomicReference[Instant](new Instant())

  override def lastInstant: Instant = lastChainingInstant.get()

  override def registerNewInstant: Instant = {
    lastChainingInstant.set(new Instant())
    lastInstant
  }

  override def elapsedSeconds: Long = lastInstant.secondsBetween(new Instant())
}
