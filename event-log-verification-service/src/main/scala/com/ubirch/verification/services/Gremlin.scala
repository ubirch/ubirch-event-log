package com.ubirch.verification.services

import gremlin.scala.{ ScalaGraph, TraversalSource }
import org.apache.tinkerpop.gremlin.process.traversal.Bindings

trait Gremlin {
  implicit def graph: ScalaGraph

  def b: Bindings

  def g: TraversalSource
}
