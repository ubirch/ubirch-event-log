package com.ubirch

import com.ubirch.models.Node

object ChainerService extends App {

  def getEmptyNodeVal = {
    val uuid =java.util.UUID.randomUUID().toString
    s"emptyNode_$uuid"
  }

  // This is an example that shows how we
  // - Take a list of seeds and turn them into empty nodes -no left or right nodes-
  // - Balance the list to have an even number of nodes.
  // - The list of  is balanced -if needed- with a value we pass in
  // - Join the empty nodes with a function that unites the values and left and right
  // - nodes to later have a node of these nodes.

  val node = Node.seeds("a", "b", "c")
    .balanceRightWithEmpty(getEmptyNodeVal)
    .join((a, b) => a + b)

  println(node)

}
