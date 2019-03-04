package com.ubirch.models

import com.ubirch.util.Hasher

/**
  * Represents a class that allows chaining values of type T
  * @param es Represents the list of elements to chain
  * @param ev Represents a conversion expected. We need type T to be
  *           Chainable
  * @tparam T Represents the type T of the elements to chain.
  */
class Chainer[T](es: List[T])(implicit ev: T => Chainable[T]) {

  private var grouped: List[List[T]] = Nil
  private var seedHashes: List[List[String]] = Nil
  private var seedNodes: List[Node[String]] = Nil
  private var node: Option[Node[String]] = None

  def getGroups: List[List[T]] = grouped

  def getHashes: List[List[String]] = seedHashes

  def getNodes: List[Node[String]] = seedNodes

  def getNode: Option[Node[String]] = node

  def createGroups: Chainer[T] = {
    grouped = es.groupBy(x => x.id).values.toList
    this
  }

  def createSeedHashes: Chainer[T] = {
    seedHashes = grouped.map(e => e.map(_.hash))
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
    val uuid = java.util.UUID.randomUUID().toString
    Hasher.hash(s"emptyNode_$uuid")
  }

  def apply[T](es: List[T])(implicit ev: T => Chainable[T]): Chainer[T] = new Chainer[T](es) {}
}

