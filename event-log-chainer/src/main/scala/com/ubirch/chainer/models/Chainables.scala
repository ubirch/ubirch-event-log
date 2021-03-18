package com.ubirch.chainer.models

import com.ubirch.chainer.util.Hasher
import com.ubirch.models.EventLog

import scala.language.implicitConversions

/**
  * Represents an implicit transformation to create chainable objects.
  * This is key in the chaining process. Basically, any structure that has an id can be chained.
  */
object Chainables {

  implicit def eventLogChainable(t: EventLog): Chainable[EventLog, String, String] = {
    new Chainable[EventLog, String, String](t) {
      require(t.id.nonEmpty, "Hash Part One is Empty")
      require(t.nonce.nonEmpty, "Hash Part Two is Empty")

      override def hash: String = Hasher.mergeAndHash(t.id, t.nonce)
      override def groupId: String = t.customerId
    }
  }

}
