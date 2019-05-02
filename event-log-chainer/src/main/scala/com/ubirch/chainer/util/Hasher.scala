package com.ubirch.chainer.util
import com.roundeights.hasher.Implicits._

trait Hasher {

  def hash(v: String): String = v.sha512.hex

  def mergeAndHash(v1: String, v2: String): String = hash(v1 + v2)

  def hashAndHash(v1: String, v2: String): String = hash(v1) + hash(v2)

}

object Hasher extends Hasher
