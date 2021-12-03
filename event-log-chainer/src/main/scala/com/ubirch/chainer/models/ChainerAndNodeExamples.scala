package com.ubirch.chainer.models

import com.ubirch.chainer.models.Hash.StringData
import com.ubirch.util.JsonHelper

import org.bouncycastle.util.encoders.Hex

import java.nio.charset.StandardCharsets

object ChainerService extends App {

  // This is an example that shows how we
  // - Take a list of seeds and turn them into empty nodes -no left or right nodes-
  // - Balance the list to have an even number of nodes.
  // - The list of  is balanced -if needed- with a value we pass in
  // - Join the empty nodes with a function that unites the values and left and right
  // - nodes to later have a node of these nodes.

  val node = Node.seeds("a", "b", "c")
    .balanceRightWithEmpty(Chainer.getEmptyNode.rawValue)
    .join((a, b) => a + b)

  println(node)

}

object ChainerService2 extends App {

  import scala.language.implicitConversions

  case class SomeDataTypeFromKafka(id: String, data: String)

  object SomeDataTypeFromKafka {
    implicit def chainable(t: SomeDataTypeFromKafka): Chainable[SomeDataTypeFromKafka, String, String] =
      new Chainable[SomeDataTypeFromKafka, String, String](t) {
        override def groupId: String = source.id
        override def hash: String = Hash(StringData(source.data)).toHexStringData.rawValue
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
  // We set our balancer func and our hashing func
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
    .withBalancerFunc(_ => Chainer.getEmptyNode.rawValue)
    .withMergeProtocol(MergeProtocol.V2_HexString)
    .withGeneralGrouping
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

object ChainerServiceBytes extends App {

  import scala.language.implicitConversions

  case class SomeDataTypeFromKafka(id: String, data: String)

  object SomeDataTypeFromKafka {

    implicit def chainable(t: SomeDataTypeFromKafka) =
      new Chainable[SomeDataTypeFromKafka, Array[Byte], Array[Byte]](t) {
        override def groupId: Array[Byte] = t.id.getBytes(StandardCharsets.UTF_8)
        override def hash: Array[Byte] = Hash(StringData(t.data)).rawValue
      }
  }

  val listOfData = List(
    SomeDataTypeFromKafka("vegetables", "eggplant"),
    SomeDataTypeFromKafka("vegetables", "artichoke")
  )

  // We pass in the data from kafka that needs to be chainable.
  // See implicit conversion.
  // We set our balancer func and our hashing func
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
    .withBalancerFunc(_ => Chainer.getEmptyNode.toBytesData.rawValue)
    .withMergeProtocol(MergeProtocol.V2_Bytes)
    .withGeneralGrouping
    .createSeedHashes
    .createSeedNodes()
    .getNodes

  println(JsonHelper.ToJson(nodes.map(x => x.map(x => Hex.toHexString(x)))).pretty)

}
