package com.ubirch.models

case class Vertex(id: String, label: Option[String], properties: Map[String, String]) {
  def addLabel(newLabel: String): Vertex = copy(label = Some(newLabel))
}

object Vertex {
  def apply(id: String): Vertex = new Vertex(id, label = None, properties = Map.empty)
}

case class Edge(properties: Map[String, String], label: Option[String])

object Edge {
  def empty = Edge(Map.empty, None)
}

case class Relation(v1: Vertex, v2: Vertex, edge: Edge) {
  def addProperty(key: String, value: String): Relation = copy(edge = Edge(edge.properties ++ Map(key -> value), edge.label))
  def withProperties(newProps: Map[String, String]): Relation = copy(edge = Edge(newProps, edge.label))
  def addRelationLabel(label: String): Relation = copy(edge = edge.copy(label = Some(label)))
  def addRelationLabel(label: Option[String]): Relation = copy(edge = edge.copy(label = label))
  def addOriginLabel(label: String): Relation = copy(v1 = v1.copy(label = Some(label)))
  def addOriginLabel(label: Option[String]): Relation = copy(v1 = v1.copy(label = label))
  def addTargetLabel(label: String): Relation = copy(v2 = v2.copy(label = Some(label)))
  def addTargetLabel(label: Option[String]): Relation = copy(v2 = v2.copy(label = label))

}

object Relation {

  def apply(v1: Vertex, v2: Vertex): Relation = new Relation(v1, v2, Edge.empty)

  def fromEventLog(eventLog: EventLog): Seq[Relation] = {
    eventLog.lookupKeys.flatMap { x =>
      x.value.map { target =>
        Relation(Vertex(x.key.name), Vertex(target.name))
          .addOriginLabel(x.key.label)
          .addTargetLabel(target.label)
          .addRelationLabel(x.category)
          .addProperty(Values.CATEGORY_LABEL, x.category)
          .addProperty(Values.NAME_LABEL, x.name)
      }
    }
  }

}
