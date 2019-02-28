package com.ubirch.models

import com.ubirch.TestBase

class NodeSpec extends TestBase {

  "A Node" must {
    "allow value and left and right Nodes" in {
      val value = "value"
      val left = Node.empty("left")
      val right = Node.empty("right")

      val node = Node.empty(value).withLeft(left).withRight(right)

      assert(node.value == value)
      assert(node.left.contains(left))
      assert(node.right.contains(right))

      val node1 = Node(value, Some(left), Some(right))

      assert(node1.value == value)
      assert(node1.left.contains(left))
      assert(node1.right.contains(right))

    }

    "allow empty node" in {

      val node = Node.empty(value)
      assert(node.value == value)
      assert(node.left.isEmpty)
      assert(node.right.isEmpty)

    }


    "add left and right node with withs helpers" in {
      val value = "value"
      val left = Node.empty("left")
      val right = Node.empty("right")

      val node = Node.empty(value).withLeft(left).withRight(right)
      val node1 = Node(value, Some(left), Some(right))

      assert(node.value == node1.value)
      assert(node.left == node1.left)
      assert(node.right == node1.right)
    }

    "add new value with WithValue" in {
      val value = "value"
      val node = Node.empty(value).withValue(value.toUpperCase)

      assert(node.value == value.toUpperCase())
      assert(node.value != value)

    }

    "has and is helpers should return as expected" in {
      val value = "value"
      val node = Node.empty(value)

      assert(!node.hasLeft)
      assert(!node.hasRight)
      assert(node.isLast)

      val left = Node.empty("left")
      val right = Node.empty("right")

      val node1 = node.withLeft(left).withRight(right)

      assert(node1.hasLeft)
      assert(node1.hasRight)
      assert(!node1.isLast)

    }

    "map" in {
      val value = "value"
      val node = Node.empty(value)
      assert(node.map(_.toUpperCase).value == node.value.toUpperCase)

      val left = Node.empty("left")
      val right = Node.empty("right")

      val node1 = node.withLeft(left).withRight(right).map(_.toUpperCase)

      assert(node1.value == value.toUpperCase)
      assert(node1.left.map(_.value).contains("left".toUpperCase()))
      assert(node1.right.map(_.value).contains("right".toUpperCase()))

    }


  }
}
