package com.ubirch.chainer.models

import com.ubirch.chainer.util.BalancingList

import scala.annotation.tailrec
import scala.language.implicitConversions

/**
  * Represents a node-based tree
  * @param value Represents the value of the node
  * @param left Represents the left node of the node
  * @param right Represents the right node of the node
  * @tparam A Represents the type of node.
  */
case class Node[A](value: A, left: Option[Node[A]], right: Option[Node[A]]) {

  def withLeft(newLeft: Node[A]): Node[A] = copy(left = Option(newLeft))

  def withRight(newRight: Node[A]): Node[A] = copy(right = Option(newRight))

  def withValue(newValue: A): Node[A] = copy(value = newValue)

  def clearLeftAndRight: Node[A] = copy(left = None, right = None)

  def hasLeft: Boolean = left.isDefined

  def hasRight: Boolean = right.isDefined

  def isLast: Boolean = !(hasRight && hasLeft)

  def map[B](f: A => B): Node[B] = Node.map(this)(f)

  def depth: Int = Node.depth(this)

  def size: Int = Node.size(this)

}

/**
  * Represents the companion object of the type Node.
  * It has very useful functions that enhance the type
  */
object Node {

  def empty[A](value: A): Node[A] = Node[A](value, None, None)

  def balanceRight[B](balancingValue: => Node[B])(values: List[Node[B]]): List[Node[B]] = {
    BalancingList.balanceRight(balancingValue)(values)
  }

  def balanceLeft[A](balancingValue: => Node[A])(values: List[Node[A]]): List[Node[A]] = {
    BalancingList.balanceLeft(balancingValue)(values)
  }

  def seeds[A](value: A*): List[Node[A]] = {
    value.map(x => Node.empty(x)).toList
  }

  /**
    * This operator turns a list of nodes into list of one node.
    * The nodes are joined/merge into one single node.
    * This join2 doesn't guaranty the order.
    */
  @tailrec
  def joinCore[A](nodes: List[Node[A]])(f: (A, A) => A)(g: (Node[A], List[Node[A]]) => List[Node[A]]): List[Node[A]] = {
    nodes match {
      case n1 :: n2 :: nx =>
        joinCore(g(Node(f(n1.value, n2.value), Some(n1), Some(n2)), nx))(f)(g)
      case _ => nodes
    }
  }

  def joinRight[A](nodes: List[Node[A]])(f: (A, A) => A): List[Node[A]] = {
    def g(n: Node[A], nx: List[Node[A]]): List[Node[A]] = n +: nx
    joinCore(nodes)(f)(g)
  }

  def join[A](nodes: List[Node[A]])(f: (A, A) => A): List[Node[A]] = {
    def g(n: Node[A], nx: List[Node[A]]): List[Node[A]] = nx ++ List(n)
    joinCore(nodes)(f)(g)
  }

  def depth[A](node: Node[A]): Int = {
    node match {
      case Node(_, None, None) => 0
      case Node(_, l, r) => 1 + l.map(x => depth(x)).getOrElse(0) max r.map(x => depth(x)).getOrElse(0)
    }

  }

  def size[A](node: Node[A]): Int = {
    node match {
      case Node(_, None, None) => 1
      case Node(_, l, r) => 1 + l.map(x => size(x)).getOrElse(0) + r.map(x => size(x)).getOrElse(0)
    }

  }

  def map[A, B](node: Node[A])(f: A => B): Node[B] = {
    Node[B](f(node.value), node.left.map(_.map(f)), node.right.map(_.map(f)))
  }

  /**
    * This operator turns a list of nodes into list of one node.
    * The nodes are joined/merge into one single node.
    * This join2 guaranties the order.
    */
  @tailrec
  def join2[A](acc: List[Node[A]], nodes: List[Node[A]])(f: (A, A) => A): List[Node[A]] = {
    nodes match {
      case Nil if acc.isEmpty || acc.size == 1 => acc
      case Nil => join2(Nil, acc)(f)
      case n1 :: n2 :: nx =>
        val newNode = acc ++ List(Node(f(n1.value, n2.value), Some(n1), Some(n2)))
        join2(newNode, nx)(f)
      case n :: xs =>
        val newNode = acc ++ List(n)
        join2(newNode, xs)(f)
    }
  }

  case class EnrichedListOfNodes[A](values: List[Node[A]]) {

    def balanceRightWithEmpty(balancingValue: => A): List[Node[A]] = Node.balanceRight(Node.empty(balancingValue))(values)

    def balanceLeftWithEmpty(balancingValue: => A): List[Node[A]] = Node.balanceLeft(Node.empty(balancingValue))(values)

    def balanceRight(balancingValue: => Node[A]): List[Node[A]] = Node.balanceRight(balancingValue)(values)

    def balanceLeft(balancingValue: => Node[A]): List[Node[A]] = Node.balanceLeft(balancingValue)(values)

    def joinRight(f: (A, A) => A): List[Node[A]] = Node.joinRight(values)(f)

    def join(f: (A, A) => A): List[Node[A]] = Node.join(values)(f)

    def join2(f: (A, A) => A): List[Node[A]] = Node.join2(Nil, values)(f)

  }

  implicit def enrichedListOfNodes[A](values: List[Node[A]]): EnrichedListOfNodes[A] = EnrichedListOfNodes[A](values)

}

