package com.ubirch.models

case class LookupKey(name: String, category: String, key: String, value: Seq[String])

object LookupKey {
  final val SLAVE_TREE = "UBIRCH_SLAVE_TREE"
  final val SLAVE_TREE_ID = "slave-tree-id"

}
