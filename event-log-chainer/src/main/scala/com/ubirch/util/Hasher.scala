package com.ubirch.util
import com.roundeights.hasher.Implicits._

trait Hasher {

  def hash(v: String): String = v.sha512.hex

  def mergeAndHash(v1: String, v2: String): String = hash(v1 + v2)
}

object Hasher extends Hasher
