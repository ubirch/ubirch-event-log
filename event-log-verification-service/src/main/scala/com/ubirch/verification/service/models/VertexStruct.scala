package com.ubirch.verification.service.models

import java.util.Date

import scala.collection.JavaConverters._

case class VertexStruct(id: String, label: String, properties: Map[String, Any]) {

  def toDumbVertexStruct = DumbVertexStruct(label, properties)

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

object VertexStruct {
  def fromMap(mapFrom: java.util.Map[AnyRef, AnyRef]): VertexStruct = {
    val newMap = mapFrom.asScala.toMap.map(p => p._1.toString -> p._2)
    val id = newMap.get("id") match {
      case Some(value) => value.toString
      case None => "ERROR"
    }
    val label = newMap.get("label") match {
      case Some(value) => value.asInstanceOf[String]
      case None => "ERROR"
    }
    val valueMap = newMap.filter(p => p._1 != "id" && p._1 != "label").map { p =>
      val value = p._2 match {
        case date: Date => date
        case whatever => whatever.toString
      }
      p._1 -> value
    }
    VertexStruct(id, label, valueMap)
  }
}

/**
  * Simple class that mimicate the class above but without ID, that simplify the JSON process
  */
case class DumbVertexStruct(label: String, properties: Map[String, Any])
