package com.ubirch.chainer.models

import com.ubirch.chainer.util.Hasher
import com.ubirch.models.EventLog

import scala.language.implicitConversions

object Chainables {

  implicit def eventLogChainable(t: EventLog): Chainable[EventLog] = new Chainable(t.customerId, t) {
    override def hash: String = Hasher.mergeAndHash(t.id, t.nonce)
  }

}
