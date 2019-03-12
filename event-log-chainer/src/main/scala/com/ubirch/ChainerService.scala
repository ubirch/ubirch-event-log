package com.ubirch

import com.ubirch.models.{ Chainable, Chainer, Node }
import com.ubirch.util.JsonHelper

object ChainerService extends App {

  // This is an example that shows how we
  // - Take a list of seeds and turn them into empty nodes -no left or right nodes-
  // - Balance the list to have an even number of nodes.
  // - The list of  is balanced -if needed- with a value we pass in
  // - Join the empty nodes with a function that unites the values and left and right
  // - nodes to later have a node of these nodes.

  val node = Node.seeds("a", "b", "c")
    .balanceRightWithEmpty(Chainer.getEmptyNodeVal)
    .join((a, b) => a + b)

  println(node)

}

object ChainerService2 extends App {

  import scala.language.implicitConversions

  case class SomeDataTypeFromKafka(id: String, data: String)

  object SomeDataTypeFromKafka {
    implicit def chainable(t: SomeDataTypeFromKafka): Chainable[SomeDataTypeFromKafka] = Chainable(t.id, t)
  }

  val listOfData = List(
    SomeDataTypeFromKafka("vegetables", "eggplant"),
    SomeDataTypeFromKafka("vegetables", "artichoke"),
    SomeDataTypeFromKafka("fruits", "banana")
  )

  // We pass in the data from kafka that needs to be chainable.
  // See implicit conversion.
  // We group the elems
  // We take the hashes
  // We then turn the seed hashes into seed nodes.
  // We then turn the seed nodes into joined node.
  val nodes = Chainer(listOfData)
    .createGroups
    .createSeedHashes
    .createSeedNodes()
    .createNode
    .getNode

  println(JsonHelper.ToJson(nodes).pretty)

}

object ChainerService3 extends App {

  // This is an example that shows how we
  // - Take a list of seeds and turn them into empty nodes -no left or right nodes-
  // - Balance the list to have an even number of nodes.
  // - The list of  is balanced -if needed- with a value we pass in
  // - Join the empty nodes with a function that unites the values and left and right
  // - nodes to later have a node of these nodes.
  //
  val values = ('a' to 'y').toList.map(_.toString)
  val node = Node.seeds(values: _*).balanceRightWithEmpty("1")
    .join2((a, b) => a + b)

  println(JsonHelper.ToJson(node).pretty)

}
