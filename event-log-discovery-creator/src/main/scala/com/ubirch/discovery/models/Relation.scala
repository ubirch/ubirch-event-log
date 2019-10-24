package com.ubirch.discovery.models

import com.ubirch.models.{ EventLog, Values }
import scala.language.implicitConversions

sealed trait RelationElem {
  val label: Option[String]
  val properties: Map[String, Any]
  def addLabel(newLabel: String): RelationElem
  def addProperty(prop: (String, Any)*): RelationElem
}

case class Vertex(label: Option[String], properties: Map[String, Any]) extends RelationElem {
  def addLabel(newLabel: String): Vertex = copy(label = Some(newLabel))
  def addProperty(prop: (String, Any)*): Vertex = copy(properties = properties ++ prop)
}

object Vertex {
  def apply(label: String): Vertex = simple(label)
  def simple(label: String): Vertex = Vertex(Some(label), Map.empty[String, String])
  def empty: Vertex = Vertex(None, Map.empty[String, String])
}

case class Edge(label: Option[String], properties: Map[String, Any]) extends RelationElem {
  def addLabel(newLabel: String): Edge = copy(label = Some(newLabel))
  def addProperty(prop: (String, Any)*): Edge = copy(properties = properties ++ prop)
}

object Edge {
  def apply(label: String): Edge = simple(label)
  def simple(label: String): Edge = Edge(Some(label), Map.empty[String, String])
  def empty: Edge = Edge(None, Map.empty[String, String])
}

case class Relation(vFrom: Vertex, vTo: Vertex, edge: Edge) {
  def addProperty(key: String, value: Any): Relation = copy(edge = Edge(edge.label, edge.properties ++ Map(key -> value)))
  def withProperties(newProps: Map[String, Any]): Relation = copy(edge = Edge(edge.label, newProps))
  def addRelationLabel(label: String): Relation = copy(edge = edge.copy(label = Some(label)))
  def addRelationLabel(label: Option[String]): Relation = copy(edge = edge.copy(label = label))
  def addOriginLabel(label: String): Relation = copy(vFrom = vFrom.copy(label = Some(label)))
  def addOriginLabel(label: Option[String]): Relation = copy(vFrom = vFrom.copy(label = label))
  def addTargetLabel(label: String): Relation = copy(vTo = vTo.copy(label = Some(label)))
  def addTargetLabel(label: Option[String]): Relation = copy(vTo = vTo.copy(label = label))
  def withEdge(newEdge: Edge): Relation = copy(edge = newEdge)

}

object Relation {

  def apply(vFrom: Vertex, vTo: Vertex): Relation = new Relation(vFrom, vTo, Edge.empty)

  def fromEventLog(eventLog: EventLog): Seq[Relation] = {
    eventLog.lookupKeys.flatMap { x =>
      x.value.map { target =>
        Relation(Vertex.simple(x.key.name), Vertex.simple(target.name))
          .addOriginLabel(x.key.label)
          .addTargetLabel(target.label)
          .addRelationLabel(x.category)
          .addProperty(Values.CATEGORY_LABEL, x.category)
          .addProperty(Values.NAME_LABEL, x.name)
      }
    }
  }

  case class EnrichedVertex(vFrom: Vertex) {
    def connectedTo(vTo: Vertex) = Relation(vFrom, vTo)
    def ->(vTo: Vertex): Relation = connectedTo(vTo)
  }

  case class EnrichedRelation(relation: Relation) {
    def through(edge: Edge): Relation = relation.withEdge(edge)
    def *(edge: Edge): Relation = through(edge)
  }

  object Implicits {
    implicit def enrichedVertex(vertex: Vertex): EnrichedVertex = EnrichedVertex(vertex)
    implicit def enrichedRelation(relation: Relation): EnrichedRelation = EnrichedRelation(relation)
  }

}
