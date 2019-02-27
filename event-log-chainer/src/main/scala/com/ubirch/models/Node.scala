package com.ubirch.models

import com.ubirch.util.BalancingList

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

  def withLeft(newLeft: Node[A]): Node[A] = this.copy(left = Option(newLeft))

  def withRight(newRight: Node[A]): Node[A] = this.copy(right = Option(newRight))

  def withValue(newValue: A): Node[A] = this.copy(value = newValue)

  def hasLeft: Boolean = left.isDefined

  def hasRight: Boolean = right.isDefined

  def isLast: Boolean = hasRight && hasLeft

  def map[B](f: A => B): Node[B] = {
    Node[B](f(value), this.left.map(_.map(f)), this.right.map(_.map(f)))
  }

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

  case class EnrichedListOfNodes[A](values: List[Node[A]]) {

    def balanceRightWithEmpty(balancingValue: => A): List[Node[A]] = Node.balanceRight(Node.empty(balancingValue))(values)

    def balanceLeftWithEmpty(balancingValue: => A): List[Node[A]] = Node.balanceLeft(Node.empty(balancingValue))(values)

    def balanceRight(balancingValue: => Node[A]): List[Node[A]] = Node.balanceRight(balancingValue)(values)

    def balanceLeft(balancingValue: => Node[A]): List[Node[A]] = Node.balanceLeft(balancingValue)(values)

    def joinRight(f: (A, A) => A): List[Node[A]] = Node.joinRight(values)(f)

    def join(f: (A, A) => A): List[Node[A]] = Node.join(values)(f)

  }

  implicit def enrichedListOfNodes[A](values: List[Node[A]]): EnrichedListOfNodes[A] = EnrichedListOfNodes[A](values)

}

