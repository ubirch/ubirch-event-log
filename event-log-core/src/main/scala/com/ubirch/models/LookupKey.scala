package com.ubirch.models

case class LookupKey(name: String, key: String, value: Seq[String])

object LookupKey {
  final val SLAVE_TREE = "slave-tree"
}
