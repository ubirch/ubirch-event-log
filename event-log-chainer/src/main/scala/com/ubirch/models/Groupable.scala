package com.ubirch.models

import com.ubirch.util.Hasher

/**
  * Represents a type with an id that is used for grouping purposes.
  * @tparam T Represents the type T that will be groupable
  */
trait Groupable[T] {
  def id: String
}

/**
  * Represents that a type T can be hashable.
  * @tparam T Represents the type T that will be hashable
  */
trait Hashable[T] {
  def hash: String
}

/**
  * Represents a type that allows a elem of type T to be chained.
  * Basically we require that T has an id so that it is groupable and that
  * it can be hashed.
  * @param id Represents the id of the group
  * @param t Represents the type that will be chained
  * @tparam T Represents the type that will be turned into chainable.
  */
case class Chainable[T](id: String, t: T) extends Groupable[T] with Hashable[T] {
  override def hash: String = Hasher.hash(t.toString)
}
