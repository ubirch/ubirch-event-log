package com.ubirch.util

/**
  * Represents a decision about a commit.
  * @tparam T Represents the type of the record.
  */
sealed trait Decision[T]

/**
  * Represents a Go ahead and commit it.
  * @param value Represents the value of the container
  * @tparam T Represent the type of the value
  */
case class Go[T](value: T) extends Decision[T]

/**
  * Represents an Ignore signal.
  * @tparam T Represents the type of decision
  */
case class Ignore[T]() extends Decision[T]

