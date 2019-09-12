package com.ubirch.chainer.models

import com.ubirch.chainer.util.Hasher
import com.ubirch.util.UUIDHelper

/**
  * Represents a class that allows chaining values of type T
  * @param es Represents the list of elements to chain
  * @param ev Represents a conversion expected. We need type T to be
  *           Chainable
  * @tparam T Represents the type T of the elements to chain.
  */
class Chainer[T](val es: List[T])(implicit ev: T => Chainable[T]) {

  private var zero: String = ""
  private var grouped: List[List[T]] = Nil
  private var seedHashes: List[List[String]] = Nil
  private var seedNodes: List[Node[String]] = Nil
  private var node: Option[Node[String]] = None

  def getZero: String = zero

  def getGroups: List[List[T]] = grouped

  def getHashes: List[List[String]] = seedHashes

  def getNodes: List[Node[String]] = seedNodes

  def getNode: Option[Node[String]] = node

  def createGroups: Chainer[T] = {
    grouped = es.groupBy(x => x.id).values.toList
    this
  }

  def withGeneralGrouping: Chainer[T] = {
    grouped = List(es)
    this
  }

  def createSeedHashes: Chainer[T] = {
    val gd = grouped.map(e => e.map(_.hash))
    seedHashes = if (zero.isEmpty) gd else List(List(zero)) ++ gd
    this
  }

  def withHashZero(zeroHash: String): Chainer[T] = {
    require(seedNodes.isEmpty && node.isEmpty, "Can't use 'createSeedHashesWithHashZero' on a chainer that has already be created as it won't have any effect on the node.")
    require(seedHashes.isEmpty, "Can't use 'createSeedHashesWithHashZero' on a chainer whose seed hashes have already been created")
    zero = zeroHash
    this
  }

  def createSeedNodes(keepOrder: Boolean = false): Chainer[T] = {
    if (keepOrder) createSeedNodesF(hashesToNodesWithJoin2)
    else createSeedNodesF(hashesToNodesWithJoin)

    this
  }

  private def createSeedNodesF(f: List[String] => List[Node[String]]): Chainer[T] = {
    seedNodes = seedHashes.flatMap(x => f(x))
    this
  }

  private def hashesToNodesWithJoin(hes: List[String]): List[Node[String]] = {
    Node.seeds(hes: _*)
      .balanceRightWithEmpty(balancingHash)
      .join((t1, t2) => Hasher.mergeAndHash(t1, t2))
  }

  private def hashesToNodesWithJoin2(hes: List[String]): List[Node[String]] = {
    Node.seeds(hes: _*)
      .balanceRightWithEmpty(balancingHash)
      .join2((t1, t2) => Hasher.mergeAndHash(t1, t2))
  }

  def balancingHash: String = Chainer.getEmptyNodeVal

  def createNode: Chainer[T] = {
    node = seedNodes.join((t1, t2) => Hasher.mergeAndHash(t1, t2)).headOption
    this
  }

  //  def h(es: List[Node[Block]]): Node[Block]
  //
  //  def i(node: Node[Block]): List[Node[Block]]

}

/**
  * Represents the companion object of the Chainer
  */
object Chainer {

  def getEmptyNodeVal: String = {
    Hasher.hash(s"emptyNode_${UUIDHelper.randomUUID}")
  }

  def getNonce: String = {
    Hasher.hash(s"Nonce_${UUIDHelper.randomUUID}")
  }

  def apply[T](es: List[T])(implicit ev: T => Chainable[T]): Chainer[T] = new Chainer[T](es) {}
}

