package com.ubirch.models

case class Vertex(id: String, label: Option[String], properties: Map[String, String])

object Vertex {
  def apply(id: String): Vertex = new Vertex(id, label = None, properties = Map.empty)
}

case class Edge(properties: Map[String, String])

object Edge {
  def empty = Edge(Map.empty)
}

case class Relation(v1: Vertex, v2: Vertex, edge: Edge) {
  def addProperty(key: String, value: String): Relation = copy(edge = Edge(edge.properties ++ Map(key -> value)))
  def withProperties(newProps: Map[String, String]): Relation = copy(edge = Edge(newProps))

}

object Relation {

  def apply(v1: Vertex, v2: Vertex): Relation = new Relation(v1, v2, Edge.empty)

  def fromEventLog(eventLog: EventLog): Seq[Relation] = {
    eventLog.lookupKeys.flatMap { x =>
      x.value.map { y =>
        Relation(Vertex(x.key), Vertex(y))
          .addProperty(Values.CATEGORY_LABEL, x.category)
          .addProperty(Values.NAME_LABEL, x.name)
      }
    }
  }

}
