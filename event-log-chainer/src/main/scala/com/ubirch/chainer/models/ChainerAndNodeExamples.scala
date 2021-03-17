package com.ubirch.chainer.models

import com.ubirch.chainer.util.Hasher
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
    implicit def chainable(t: SomeDataTypeFromKafka): Chainable[SomeDataTypeFromKafka, String, String] =
      new Chainable[SomeDataTypeFromKafka, String, String](t) {
        override def groupId: String = t.id
        override def hash: String = Hasher.hash(t.data)
      }
  }

  val listOfData = List(
    SomeDataTypeFromKafka("vegetables", "eggplant"),
    SomeDataTypeFromKafka("vegetables", "artichoke"),
    SomeDataTypeFromKafka("vegetables", "Gurke"),
    SomeDataTypeFromKafka("vegetables", "Feldsalat"),
    SomeDataTypeFromKafka("vegetables", "Kohl"),
    SomeDataTypeFromKafka("fruits", "banana"),
    SomeDataTypeFromKafka("fruits", "apple"),
    SomeDataTypeFromKafka("fruits", "cherry")
  )

  // We pass in the data from kafka that needs to be chainable.
  // See implicit conversion.
  // We group the elems
  // We take the hashes
  // We then turn the seed hashes into seed nodes.
  // We then turn the seed nodes into joined node.
  //  val nodes = Chainer(listOfData)
  //    .createGroups
  //    .createSeedHashes
  //    .createSeedNodes()
  //    .createNode
  //    .getNode

  val nodes = Chainer(listOfData)
    .withBalancerFunc(_ => Chainer.getEmptyNodeVal)
    .withMergerFunc(Hasher.mergeAndHash)
    .createGroups
    .createSeedHashes
    .createSeedNodes()
    .getNodes

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
