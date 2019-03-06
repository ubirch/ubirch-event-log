package com.ubirch.services.kafka.consumer

/**
  * Represents a simple callback data type that takes no parameters
  *
  * @tparam B Represents the output of the callback
  */
trait Callback0[B] {

  var callbacks = Vector.empty[() => B]

  def addCallback(f: () => B): Unit = {
    callbacks = callbacks ++ Vector(f)
  }

  def run(): Unit = callbacks.foreach(x => x())

}

/**
  * Represents a simple callback data type that takes one parameter of the A
  *
  * @tparam A Represents the input of the callback
  * @tparam B Represents the output of the callback
  */
trait Callback[A, B] extends {

  var callbacks = Vector.empty[A => B]

  def addCallback(f: A => B): Unit = {
    callbacks = callbacks ++ Vector(f)
  }

  def run(a: A): Vector[B] = callbacks.map(x => x(a))

}
