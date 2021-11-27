package com.ubirch.chainer.models

import com.ubirch.chainer.models.Hash.{ HexStringData, StringData }
import com.ubirch.util.UUIDHelper

import scala.annotation.tailrec

/**
  * Represents a type with an id/field that is used for grouping purposes.
  * @tparam G Represents the type G that will be groupable
  */
trait Groupable[+G] {
  def groupId: G
}

/**
  * Represents that a type H can be hashable.
  * @tparam H Represents the type H that will be hashable
  */
trait Hashable[+H] {
  def hash: H
}

/***
 * Represents a type that allows a elem of type T to be chained.
 * Basically we require that T has an id so that it is groupable and that
 * it can be hashed.
 * @param t Represents the type that will be chained
 * @tparam T Represents the type that will be turned into chainable.
 * @tparam G Represents the type G that will be groupable
 * @tparam H Represents the type H that will be hashable
 */
abstract class Chainable[T, +G, +H](t: T) extends Groupable[G] with Hashable[H] {
  def source: T = t
  def hash: H
}

/**
  * Represents a class that allows chaining values of type T
  * @param es Represents the list of elements to chain
  * @param ev Represents a conversion expected. We need type T to be
  *           Chainable
  * @tparam T Represents the type T of the elements to chain.
  * @tparam G Represents the type G that will be groupable
  * @tparam H Represents the type H that will be hashable
  */
abstract class Chainer[T, G, H](es: List[T])(implicit ev: T => Chainable[T, G, H]) {

  /**
    * Represents a root hash from another chainer process.
    * It is intended to connect chainers together
    */
  private var zero: Option[H] = None
  /**
    * Represents a list of grouped leaves.
    * They are possibly sorted by a balancer function
    * List[H] => H.
    */
  private var grouped: List[List[T]] = Nil
  /**
    * Represents the leaves that have been hashed as seeds for the
    * chainer and tree creation processes.
    */
  private var seedHashes: List[List[H]] = Nil
  /**
    * Represents a Node-based tree of balanced seeds.
    * That means that before creating the node objects, a possible
    * balancing function might have been applied to its leaves.
    */
  private var balancedSeedNodes: List[Node[H]] = Nil
  /**
    * Represents a Node-based tree.
    */
  private var seedNodes: List[Node[H]] = Nil
  /**
    * Represents the whole tree.
    * The final tree.
    */
  private var node: Option[Node[H]] = None

  /**
    * It is a function that is used to join to values of type H.
    * A hash function is intended to be plugged in.
    */
  private var merger: Option[(H, H) => H] = None
  /**
    * It is a function that is used to balance the leaves in case they are not even.
    * The leaves are passed in as parameters in the function so that interesting balancing
    * options can be used.
    */
  private var balancer: Option[List[H] => H] = None

  /**
    * Represents the list of seeds, that is, the incoming data of type T.
    * Note that the incoming list gets implicitly transformed into chainable objects.
    * @return List of T that represent the seeds to the chainer.
    */
  def seeds: List[T] = es

  /**
    * Represents the a possible initial hash from another process or chainer.
    * @return Option of H
    */
  def getZero: Option[H] = zero

  /**
    * Gets the possible groups that may have been created if the
    * creation of groups has been executed.
    * This happens at the outer layer of the process. That's to say, before nodes or hashes
    * have been introduced in the process
    * @return List of Lists of T
    */
  def getGroups: List[List[T]] = grouped

  /**
    * Gets the seed hashes calculated out of incoming data
    * @return List of Lists of hashable data H
    */
  def getHashes: List[List[H]] = seedHashes

  /**
    * Gets the Nodes of Hashable data.
    * @return List of Nodes of hashable data H
    */
  def getNodes: List[Node[H]] = seedNodes

  /**
    * Gets the balanced Nodes of Hashable data
    * @return  List of balanced Nodes of hashable data H
    */
  def getBalancedNodes: List[Node[H]] = balancedSeedNodes

  /**
    * Gets the most aggregated Node of hashable data H
    * @return Option of Node of hashable data H
    */
  def getNode: Option[Node[H]] = node

  def createGroups: Chainer[T, G, H] = {
    grouped = es.groupBy(x => x.groupId).values.toList
    this
  }

  def withGeneralGrouping: Chainer[T, G, H] = {
    grouped = List(es)
    this
  }

  def createSeedHashes: Chainer[T, G, H] = {
    val gd = grouped.map(e => e.map(_.hash))

    def addZero(zero: H): List[List[H]] = {
      gd match {
        case Nil => gd
        case xs :: xss =>
          val head = zero +: xs
          head +: xss
      }
    }

    seedHashes = getZero.map(addZero).getOrElse(gd) //if (zero.isEmpty) gd else addZero
    this
  }

  def withHashZero(zeroHash: Option[H]): Chainer[T, G, H] = {
    require(seedNodes.isEmpty && node.isEmpty, "Can't use 'createSeedHashesWithHashZero' on a chainer that has already been created as it won't have any effect on the node.")
    require(seedHashes.isEmpty, "Can't use 'createSeedHashesWithHashZero' on a chainer whose seed hashes have already been created")
    zero = zeroHash
    this
  }

  def createSeedNodes(keepOrder: Boolean = false): Chainer[T, G, H] = {
    if (keepOrder) createSeedNodesF(hashesToNodesWithJoin2)
    else createSeedNodesF(hashesToNodesWithJoin)

    this
  }

  private def createSeedNodesF(f: List[H] => List[Node[H]]): Chainer[T, G, H] = {
    seedNodes = seedHashes.flatMap(x => f(x))
    this
  }

  private def balance(hes: List[H]): List[Node[H]] = {
    val maybeBalancer = balancer.map(x => x(hes))
    require(maybeBalancer.isDefined, "Cannot balance with unset balancer")
    val balanced = maybeBalancer.toList.flatMap(bh => Node.seeds(hes: _*).balanceRightWithEmpty(bh))
    balancedSeedNodes = balanced
    balanced
  }

  private def hashesToNodesWithJoin(hes: List[H]): List[Node[H]] = {
    merger.toList.flatMap { m => balance(hes).join((t1, t2) => m(t1, t2)) }
  }

  private def hashesToNodesWithJoin2(hes: List[H]): List[Node[H]] = {
    merger.toList.flatMap { m => balance(hes).join2((t1, t2) => m(t1, t2)) }
  }

  def createNode: Chainer[T, G, H] = {
    node = merger.flatMap { m => seedNodes.join((t1, t2) => m(t1, t2)).headOption }
    this
  }

  def compress: Option[CompressedTreeData[H]] = Chainer.compress(this)

  def withMergerFunc(newMerger: (H, H) => H): Chainer[T, G, H] = {
    merger = Option(newMerger)
    this
  }

  def withBalancerFunc(newBalancer: List[H] => H): Chainer[T, G, H] = {
    balancer = Option(newBalancer)
    this
  }

}

/**
  * Represents the companion object of the Chainer
  */
object Chainer {

  def getEmptyNode: HexStringData = Hash(StringData(s"emptyNode_${UUIDHelper.randomUUID}")).toHexStringData

  def apply[T, G, H](es: List[T])(implicit ev: T => Chainable[T, G, H]): Chainer[T, G, H] = new Chainer[T, G, H](es) {}

  case class CreateConfig[H](
      maybeInitialTreeHash: Option[H],
      split: Boolean,
      splitSize: Int,
      prefixer: H => H,
      merger: (H, H) => H,
      balancer: List[H] => H
  )

  def create[T, G, H](es: List[T], config: CreateConfig[H])(implicit ev: T => Chainable[T, G, H]): (List[Chainer[T, G, H]], Option[H]) = {

    @tailrec def go(
        splits: List[List[T]],
        chainers: List[Chainer[T, G, H]],
        latestHash: Option[H]
    ): (List[Chainer[T, G, H]], Option[H]) = {
      splits match {
        case Nil => (chainers, latestHash)
        case xs :: xss =>
          val chainer = Chainer(xs)
            .withMergerFunc(config.merger)
            .withBalancerFunc(config.balancer)
            .withHashZero(latestHash)
            .withGeneralGrouping
            .createSeedHashes
            .createSeedNodes(keepOrder = true)
            .createNode

          go(
            splits = xss,
            chainers = chainers ++ List(chainer),
            latestHash = chainer.getNode.map(x => config.prefixer(x.value))
          )

      }
    }

    def split(es: List[T]): Iterator[List[T]] = {
      if (config.split && es.size >= config.splitSize * 2)
        es.sliding(config.splitSize, config.splitSize)
      else
        Iterator(es)
    }

    val splits = split(es).toList
    go(
      splits = splits,
      chainers = Nil,
      latestHash = config.maybeInitialTreeHash
    )

  }

  def compress[T, G, H](chainer: Chainer[T, G, H]): Option[CompressedTreeData[H]] =
    chainer.getNode.map { root =>
      val leaves = chainer.getBalancedNodes.map(_.value)
      CompressedTreeData(root.value, leaves)
    }

  def uncompress[H](compressedTreeData: CompressedTreeData[H])(f: (H, H) => H)(implicit comparator: (H, H) => Boolean): Option[Node[H]] = {
    val uncompressed = compressedTreeData
      .leaves
      .map(x => Node(x, None, None))
      .join2((t1, t2) => f(t1, t2))
      .headOption

    uncompressed match {
      case c @ Some(value) if comparator(value.value, compressedTreeData.root) => c
      case Some(value) if !comparator(value.value, compressedTreeData.root) => throw new Exception("Root Hash doesn't match Compressed Root")
      case None => None
    }

  }

  def checkConnectedness[H](compressed: List[CompressedTreeData[H]])(implicit comparator: (H, H) => Boolean): Boolean = {

    @tailrec
    def go(check: Boolean, compressed: List[CompressedTreeData[H]]): Boolean = {
      compressed match {
        case Nil => check
        case List(_) => check
        case x :: y :: xs =>
          val nextCheck = (Option(x.root), y.leaves.headOption) match {
            case (Some(a), Some(b)) => comparator(a, b)
            case _ => false
          }
          go(nextCheck, xs)
      }
    }

    go(check = false, compressed)

  }

}

/**
  * Represents a data simplified data structure for a tree
  * @param root Represents the root of the tree
  * @param leaves Represents the leaves of the tree
  * @tparam H Represents the type of the leaves
  */
case class CompressedTreeData[H](root: H, leaves: List[H])

