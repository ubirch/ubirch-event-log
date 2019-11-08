package com.ubirch.chainer.models

import com.ubirch.chainer.util.Hasher
import com.ubirch.util.UUIDHelper

import scala.annotation.tailrec

/**
  * Represents a class that allows chaining values of type T
  * @param es Represents the list of elements to chain
  * @param ev Represents a conversion expected. We need type T to be
  *           Chainable
  * @tparam T Represents the type T of the elements to chain.
  */
class Chainer[T](es: List[T])(implicit ev: T => Chainable[T]) {

  private var zero: String = ""
  private var grouped: List[List[T]] = Nil
  private var seedHashes: List[List[String]] = Nil
  private var balancedSeedNodes: List[Node[String]] = Nil
  private var seedNodes: List[Node[String]] = Nil
  private var node: Option[Node[String]] = None

  def seeds: List[T] = es

  def getZero: String = zero

  def getGroups: List[List[T]] = grouped

  def getHashes: List[List[String]] = seedHashes

  def getNodes: List[Node[String]] = seedNodes

  def getBalancedNodes: List[Node[String]] = balancedSeedNodes

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

    def addZero = {
      gd match {
        case Nil => gd
        case xs :: xss =>
          val head = zero +: xs
          head +: xss
      }
    }

    seedHashes = if (zero.isEmpty) gd else addZero
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

  private def balance(hes: List[String]): List[Node[String]] = {
    val balanced = Node.seeds(hes: _*).balanceRightWithEmpty(balancingHash)
    balancedSeedNodes = balanced
    balanced
  }

  private def hashesToNodesWithJoin(hes: List[String]): List[Node[String]] =
    balance(hes).join((t1, t2) => Hasher.mergeAndHash(t1, t2))

  private def hashesToNodesWithJoin2(hes: List[String]): List[Node[String]] =
    balance(hes).join2((t1, t2) => Hasher.mergeAndHash(t1, t2))

  def balancingHash: String = Chainer.getEmptyNodeVal

  def createNode: Chainer[T] = {
    node = seedNodes.join((t1, t2) => Hasher.mergeAndHash(t1, t2)).headOption
    this
  }

  def compress: Option[CompressedTreeData] = Chainer.compress(this)

}

/**
  * Represents the companion object of the Chainer
  */
object Chainer {

  def getEmptyNodeVal: String = Hasher.hash(s"emptyNode_${UUIDHelper.randomUUID}")

  def getNonce: String = Hasher.hash(s"Nonce_${UUIDHelper.randomUUID}")

  def apply[T](es: List[T])(implicit ev: T => Chainable[T]): Chainer[T] = new Chainer[T](es) {}

  case class CreateConfig(
      maybeInitialTreeHash: Option[String],
      outerBalancingHash: Option[String],
      split: Boolean,
      splitSize: Int, prefixer: String => String
  )

  def create[T](es: List[T], config: CreateConfig)(implicit ev: T => Chainable[T]): (List[Chainer[T]], String) = {

    @tailrec def go(
        splits: List[List[T]],
        chainers: List[Chainer[T]],
        latestHash: String
    ): (List[Chainer[T]], String) = {
      splits match {
        case Nil => (chainers, latestHash)
        case xs :: xss =>
          val chainer = new Chainer(xs) {
            override def balancingHash: String = config.outerBalancingHash.getOrElse(super.balancingHash)
          }
            .withHashZero(latestHash)
            .withGeneralGrouping
            .createSeedHashes
            .createSeedNodes(keepOrder = true)
            .createNode

          go(xss, chainers ++ List(chainer), chainer.getNode.map(x => config.prefixer(x.value)).getOrElse(""))

      }
    }

    def split(es: List[T]): Iterator[List[T]] = {
      if (config.split && es.size >= config.splitSize * 2)
        es.sliding(config.splitSize, config.splitSize)
      else
        Iterator(es)
    }

    val splits = split(es).toList
    go(splits, Nil, config.maybeInitialTreeHash.getOrElse(""))

  }

  def compress[T](chainer: Chainer[T]): Option[CompressedTreeData] =
    chainer.getNode.map { root =>
      val leaves = chainer.getBalancedNodes.map(_.value)
      CompressedTreeData(root.value, leaves)
    }

  def uncompress(compressedTreeData: CompressedTreeData): Option[Node[String]] = {
    val uncompressed = compressedTreeData
      .leaves
      .map(x => Node(x, None, None))
      .join2((t1, t2) => Hasher.mergeAndHash(t1, t2))
      .headOption

    uncompressed match {
      case c @ Some(value) if value.value == compressedTreeData.root => c
      case Some(value) if value.value != compressedTreeData.root => throw new Exception("Root Hash doesn't match Compressed Root")
      case None => None
    }

  }

}

