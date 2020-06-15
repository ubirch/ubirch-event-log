package com.ubirch.chainer.models

/**
  * Represents a data simplified data structure for a tree
  * @param root Represents the root of the tree
  * @param leaves Represents the leaves of the tree
  */
case class CompressedTreeData(root: String, leaves: List[String])
