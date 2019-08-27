package com.ubirch.discovery.models

import com.ubirch.models.{ EventLog, Values }

case class RelationElem(label: Option[String], properties: Map[String, String]) {
  def addLabel(newLabel: String): RelationElem = copy(label = Some(newLabel))
}

object RelationElem {
  def simple(relationElemLabel: String): RelationElem = RelationElem(Some(relationElemLabel), Map.empty[String, String])
  def empty: RelationElem = RelationElem(None, Map.empty[String, String])
}

case class Relation(v1: RelationElem, v2: RelationElem, edge: RelationElem) {
  def addProperty(key: String, value: String): Relation = copy(edge = RelationElem(edge.label, edge.properties ++ Map(key -> value)))
  def withProperties(newProps: Map[String, String]): Relation = copy(edge = RelationElem(edge.label, newProps))
  def addRelationLabel(label: String): Relation = copy(edge = edge.copy(label = Some(label)))
  def addRelationLabel(label: Option[String]): Relation = copy(edge = edge.copy(label = label))
  def addOriginLabel(label: String): Relation = copy(v1 = v1.copy(label = Some(label)))
  def addOriginLabel(label: Option[String]): Relation = copy(v1 = v1.copy(label = label))
  def addTargetLabel(label: String): Relation = copy(v2 = v2.copy(label = Some(label)))
  def addTargetLabel(label: Option[String]): Relation = copy(v2 = v2.copy(label = label))

}

object Relation {

  def apply(v1: RelationElem, v2: RelationElem): Relation = new Relation(v1, v2, RelationElem.empty)
  def apply(label: String, v1: RelationElem, v2: RelationElem): Relation = new Relation(v1, v2, RelationElem.empty.addLabel(label))

  def fromEventLog(eventLog: EventLog): Seq[Relation] = {
    eventLog.lookupKeys.flatMap { x =>
      x.value.map { target =>
        Relation(RelationElem.simple(x.key.name), RelationElem.simple(target.name))
          .addOriginLabel(x.key.label)
          .addTargetLabel(target.label)
          .addRelationLabel(x.category)
          .addProperty(Values.CATEGORY_LABEL, x.category)
          .addProperty(Values.NAME_LABEL, x.name)
      }
    }
  }

}
