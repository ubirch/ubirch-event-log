package com.ubirch.verification.services.gremlin

import gremlin.scala.{ ScalaGraph, TraversalSource }
import org.apache.tinkerpop.gremlin.process.traversal.Bindings

trait Gremlin {
  implicit def graph: ScalaGraph

  def b: Bindings

  def g: TraversalSource
}
