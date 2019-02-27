package com.ubirch

import com.ubirch.models.Node

object ChainerService extends App {

  // This is an example that shows how we
  // - Take a list of seeds and turn them into empty nodes -no left or right nodes-
  // - Balance the list to have an even number of nodes.
  // - The list of  is balanced -if needed- with a value we pass in
  // - Join the empty nodes with a function that unites the values and left and right
  // - nodes to later have a node of these nodes.

  val node = Node.seeds("a", "b", "c")
    .balanceRightWithEmpty("d")
    .join((a, b) => a + b)

}
