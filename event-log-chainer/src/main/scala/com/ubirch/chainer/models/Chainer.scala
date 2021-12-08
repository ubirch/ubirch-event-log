package com.ubirch.chainer.models

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
 *
 * We pass in the data from kafka that needs to be chainable.
 * See implicit conversion.
 * We set our balancer func and our hashing func
 * We group the elems
 * We take the hashes
 * We then turn the seed hashes into seed nodes.
 * We then turn the seed nodes into joined node.
 *  val nodes = Chainer(listOfData)
 *    .createGroups
 *    .createSeedHashes
 *    .createSeedNodes()
 *    .createNode
 *    .getNode
 *
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
  private var mergeProtocol: Option[MergeProtocol[H]] = None
  /**
    * It is a function that is used to balance the leaves in case they are not even.
    * The leaves are passed in as parameters in the function so that interesting balancing
    * options can be used.
    */
  private var balancingProtocol: Option[BalancingProtocol[H]] = None

  /**
    * Represents the list of seeds, that is, the incoming data of type T.
    * Note that the incoming list gets implicitly transformed into chainable objects.
    * @return List of T that represent the seeds to the chainer.
    */
  def seeds: List[T] = es

  /**
    * Represents an initial hash from another process or chainer.
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

  /**
    * Creates groups of related data based on the groupId
    * provided by the chainable data type
    * @return Chainer[T, G, H]
    */
  def createGroups: Chainer[T, G, H] = {
    grouped = es.groupBy(x => x.groupId).values.toList
    this
  }

  /**
    * Creates a general group. It doesn't use the groupId
    * provided by the chainable data type.
    * This is particularly useful if not grouping is required
    * @return Chainer[T, G, H]
    */
  def withGeneralGrouping: Chainer[T, G, H] = {
    grouped = List(es)
    this
  }

  /**
    * Creates the initial list of hashes that are actually used
    * for the creation of node later on.
    * It also takes into account if we know of an initial hash, a.k.a Zero.
    * @return Chainer[T, G, H]
    */
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

    seedHashes = getZero.map(addZero).getOrElse(gd)
    this
  }

  /**
    * Sets the initial hash from a previos processing phase. This first
    * hash is called Zero
    * @param zeroHash represents a possible previous hash of type H
    * @return Chainer[T, G, H]
    */
  def withHashZero(zeroHash: Option[H]): Chainer[T, G, H] = {
    require(seedNodes.isEmpty && node.isEmpty, "Can't use 'createSeedHashesWithHashZero' on a chainer that has already been created as it won't have any effect on the node.")
    require(seedHashes.isEmpty, "Can't use 'createSeedHashesWithHashZero' on a chainer whose seed hashes have already been created")
    zero = zeroHash
    this
  }

  /**
    * Sets the merging strategy for types H.
    * @param newMergeProtocol a data type that describes how to merge two types H and provide some
    *                         extra information required for de/compress purposes.
    * @return Chainer[T, G, H]
    */
  def withMergeProtocol(newMergeProtocol: MergeProtocol[H]): Chainer[T, G, H] = {
    mergeProtocol = Option(newMergeProtocol)
    this
  }

  /**
    * Gets configured merge protocol
    * @return Option of MergeProtocol of H
    */
  def getMergeProtocol: Option[MergeProtocol[H]] = {
    mergeProtocol
  }

  /**
    * Sets the balancing strategy for types H.
    * It knows how to balance a set of hashes that are not even.
    * @param newBalancingProtocol a data types that describes how to produce a new value H for balancing purposes.
    *                    it takes the list of H, so that different strategies can be composed.
    * @return Chainer[T, G, H]
    */
  def withBalancingProtocol(newBalancingProtocol: BalancingProtocol[H]): Chainer[T, G, H] = {
    balancingProtocol = Option(newBalancingProtocol)
    this
  }

  /**
    * Creates the initial list of nodes of type H.
    * This creates nodes objects out of the seed hashes.
    * @param keepOrder Makes that the order of the nodes be guarantied
    * @return Chainer[T, G, H]
    */
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
    require(hes.nonEmpty, "Cannot balance empty input")
    require(balancingProtocol.isDefined, "Cannot balance with unset balancing protocol")
    val maybeBalancingDatePoint = balancingProtocol.flatMap(x => x(hes))
    require(maybeBalancingDatePoint.isDefined, "Cannot balance with unset balancer")
    val balanced = maybeBalancingDatePoint.toList.flatMap(bh => Node.seeds(hes: _*).balanceRightWithEmpty(bh))
    balancedSeedNodes = balanced
    balanced
  }

  private def hashesToNodesWithJoin(hes: List[H]): List[Node[H]] = {
    mergeProtocol.toList.flatMap { m => balance(hes).join((t1, t2) => m.merger(t1, t2)) }
  }

  private def hashesToNodesWithJoin2(hes: List[H]): List[Node[H]] = {
    mergeProtocol.toList.flatMap { m => balance(hes).join2((t1, t2) => m.merger(t1, t2)) }
  }

  /**
    * Creates a bottom-up node. It represents the
    * ultimate aggregation of nodes into a single node.
    * @return Chainer[T, G, H]
    */
  def createNode: Chainer[T, G, H] = {
    node = mergeProtocol.flatMap { m => seedNodes.join((t1, t2) => m.merger(t1, t2)).headOption }
    this
  }

  /**
    * It converts a node into a data type that has its minimum bill of materials for the
    * creation of the node.
    * @return Option of compressed tree data of type H
    */
  def compress: Option[CompressedTreeData[H]] = Chainer.compress(this)

}

/**
  * Represents the companion object of the Chainer
  */
object Chainer {

  /**
    * Helper function to create a chainer
    * @param es Represents the list of raw values from which nodes will be created
    * @param ev Represents an implicit conversion from values of T into chainable data types
    * @tparam T Represents the type T of the elements to chain.
    * @tparam G Represents the type G that will be groupable
    * @tparam H Represents the type H that will be hashable
    * @return a chainer object that knows how to create connected nodes
    */
  def apply[T, G, H](es: List[T])(implicit ev: T => Chainable[T, G, H]): Chainer[T, G, H] = new Chainer[T, G, H](es) {}

  /**
    * Represents a configuration object for easy creation of required objects
    * @param maybeInitialTreeHash Represents a zero value from previous processes
    * @param split It indicates if the the creation process should be split
    * @param splitSize It indicates the size for the slits
    * @param prefixer It is a function that prefixes the root hash.
    * @param mergeProtocol It represents the merging protocol.
    * @param balancingProtocol It represents the balancing protocol.
    * @tparam H Represents the type H that will be hashable.
    *
    */
  case class CreateConfig[H](
      maybeInitialTreeHash: Option[H],
      split: Boolean,
      splitSize: Int,
      prefixer: H => H,
      mergeProtocol: MergeProtocol[H],
      balancingProtocol: BalancingProtocol[H]
  )

  /**
    * Helper function to create a full node
    * @param es Represents the list of raw values from which nodes will be created
    * @param config Represents a simple configuration object for the creation of the full node
    * @param ev Represents an implicit conversion from values of T into chainable data types
    * @tparam T Represents the type T of the elements to chain.
    * @tparam G Represents the type G that will be groupable
    * @tparam H Represents the type H that will be hashable
    * @return A chainer object and the last root value of the node
    */
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
            .withMergeProtocol(config.mergeProtocol)
            .withBalancingProtocol(config.balancingProtocol)
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

  /**
    * Helper function that knows how to compress chainer objects into a smaller representation that is
    * more convenient to storage or transport
    * @param chainer a class that allows chaining values of type T
    * @tparam T Represents the type T of the elements to chain.
    * @tparam G Represents the type G that will be groupable
    * @tparam H Represents the type H that will be hashable
    * @return Simple representation of the full node value.
    */
  def compress[T, G, H](chainer: Chainer[T, G, H]): Option[CompressedTreeData[H]] = {
    for {
      mp <- chainer.getMergeProtocol
      root <- chainer.getNode
    } yield {
      val leaves = chainer.getBalancedNodes.map(_.value)
      CompressedTreeData(mp.version, root.value, leaves)
    }

  }

  /**
    * Helper function that knows how to uncompress from a CompressedTreeData[H] into Node[H]
    * @param compressedTreeData Compressed value of the representation of the Node of type H
    * @param m Protocol for merging values of type H
    * @tparam H Represents the type H that will be hashable
    * @return Represents a node-based tree
    */
  def uncompress[H](compressedTreeData: CompressedTreeData[H])(m: MergeProtocol[H]): Option[Node[H]] = {

    require(compressedTreeData.version == m.version, "Compressed data has different version from detected protocol")

    val uncompressed = compressedTreeData
      .leaves
      .map(x => Node(x, None, None))
      .join2((t1, t2) => m.merger(t1, t2))
      .headOption

    uncompressed match {
      case c @ Some(value) if m.equals(value.value, compressedTreeData.root) => c
      case Some(value) if !m.equals(value.value, compressedTreeData.root) => throw new Exception("Root Hash doesn't match Compressed Root")
      case None => None
    }

  }

  /**
    * Helper function that checks the connectedness of a list of compressed tree data of type H
    * @param compressed Compressed value of the representation of the Node of type H
    * @param m Protocol for merging values of type H
    * @tparam H Represents the type H that will be hashable
    * @return boolean value from the verification
    */
  def checkConnectedness[H](compressed: List[CompressedTreeData[H]])(m: MergeProtocol[H]): Boolean = {

    require(compressed.forall(_.version == m.version), "Compressed data has different version from detected protocol")

    @tailrec
    def go(check: Boolean, compressed: List[CompressedTreeData[H]]): Boolean = {
      compressed match {
        case Nil => check
        case List(_) => check
        case x :: y :: xs =>
          val nextCheck = (Option(x.root), y.leaves.headOption) match {
            case (Some(a), Some(b)) => m.equals(a, b)
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
  *  @param version Represents the MergeProtocol version
  * @param root Represents the root of the tree
  * @param leaves Represents the leaves of the tree
  * @tparam H Represents the type of the leaves
  */
case class CompressedTreeData[H](version: Int, root: H, leaves: List[H])

