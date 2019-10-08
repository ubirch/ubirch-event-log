package com.ubirch.lookup.models

case class VertexStruct(label: String, properties: Map[String, String]) {

  def addLabel(newLabel: String): VertexStruct = copy(label = newLabel)

  def addProperties(newProperties: (String, String)*): VertexStruct =
    copy(properties = this.properties ++ newProperties)

  def addProperties(newProperties: Map[String, String]): VertexStruct =
    copy(properties = this.properties ++ newProperties)

  def withProperties(newProperties: Map[String, String]): VertexStruct =
    copy(properties = newProperties)

}
