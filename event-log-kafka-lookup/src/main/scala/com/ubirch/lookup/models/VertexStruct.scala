package com.ubirch.lookup.models

case class VertexStruct(label: String, properties: Map[String, List[String]]) {
  def addLabel(newLabel: String): VertexStruct = copy(label = newLabel)
  def addProperties(newProperties: (String, List[String])*): VertexStruct =
    copy(properties = this.properties ++ newProperties)

  def addProperties(newProperties: Map[String, List[String]]): VertexStruct =
    copy(properties = this.properties ++ newProperties)

  def withProperties(newProperties: Map[String, List[String]]): VertexStruct =
    copy(properties = newProperties)

}
