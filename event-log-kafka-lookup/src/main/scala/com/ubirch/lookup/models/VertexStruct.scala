package com.ubirch.lookup.models

case class VertexStruct(label: String, properties: Map[String, Any]) {
  def get(key: String): Option[Any] = properties.get(key)
  def addLabel(newLabel: String): VertexStruct = copy(label = newLabel)
  def addProperties(newProperties: (String, Any)*): VertexStruct = copy(properties = this.properties ++ newProperties)
  def addProperties(newProperties: Map[String, Any]): VertexStruct = copy(properties = this.properties ++ newProperties)
  def withProperties(newProperties: Map[String, Any]): VertexStruct = copy(properties = newProperties)
}
