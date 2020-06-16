package com.ubirch.verification.microservice.models

case class VertexStruct(label: String, properties: Map[String, Any]) {
  def getBoth(key1: String, key2: String): Option[(Any, Any)] = get(key1).flatMap(value1 => get(key2).map(value2 => (value1, value2)))

  def get(key: String): Option[Any] = properties.get(key)

  def addLabel(newLabel: String): VertexStruct = copy(label = newLabel)

  def addProperties(newProperties: (String, Any)*): VertexStruct = copy(properties = this.properties ++ newProperties)

  def addProperties(newProperties: Map[String, Any]): VertexStruct = copy(properties = this.properties ++ newProperties)

  def withProperties(newProperties: Map[String, Any]): VertexStruct = copy(properties = newProperties)

  def replace(newProperty: (String, Any)): VertexStruct = map(newProperty._1)(_ => newProperty._2)

  def map(key: String)(f: Any => Any): VertexStruct = copy(properties = this.properties.map { case (k, v) =>
    val newV = if (k == key) f(v) else v
    (k, newV)
  })

  def addLabelWhen(newLabel: String)(matching: String): VertexStruct = {
    if (this.label == matching) this.addLabel(newLabel)
    else this
  }
}
