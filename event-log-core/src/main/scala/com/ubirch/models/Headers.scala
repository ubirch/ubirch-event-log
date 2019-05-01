package com.ubirch.models

import java.util.Locale

import com.ubirch.util.EventLogJsonSupport

import scala.collection.immutable.{ TreeMap, TreeSet }

case class Headers(protected var _headers: Seq[(String, String)]) {

  private object CaseInsensitiveOrdered extends Ordering[String] {
    def compare(x: String, y: String): Int = {
      val xl = x.length
      val yl = y.length
      if (xl < yl) -1 else if (xl > yl) 1 else x.compareToIgnoreCase(y)
    }
  }

  def headers: Seq[(String, String)] = _headers

  def hasHeader(headerName: String): Boolean = get(headerName).isDefined

  def add(headers: (String, String)*): Headers = new Headers(this.headers ++ headers)

  def apply(key: String): String = get(key).getOrElse(scala.sys.error("Header doesn't exist"))

  def get(key: String): Option[String] = getAll(key).headOption

  def getAll(key: String): Seq[String] = toMap.getOrElse(key, Nil)

  def keys: Set[String] = toMap.keySet

  def remove(keys: String*): Headers = {
    val keySet = TreeSet(keys: _*)(CaseInsensitiveOrdered)
    new Headers(headers.filterNot { case (name, _) => keySet(name) })
  }

  def replace(headers: (String, String)*): Headers = remove(headers.map(_._1): _*).add(headers: _*)

  lazy val toMap: Map[String, Seq[String]] = {
    val builder = TreeMap.newBuilder[String, Seq[String]](CaseInsensitiveOrdered)

    headers.groupBy(_._1.toLowerCase(Locale.ENGLISH)).foreach {
      case (_, headers) =>
        // choose the case of first header as canonical
        builder += headers.head._1 -> headers.map(_._2)
    }

    builder.result()
  }

  lazy val toSimpleMap: Map[String, String] = toMap.mapValues(_.headOption.getOrElse(""))

  private lazy val lowercaseMap: Map[String, Set[String]] = toMap
    .map {
      case (name, value) => name.toLowerCase(Locale.ENGLISH) -> value
    }
    .mapValues(_.toSet)

  override def equals(that: Any): Boolean = that match {
    case other: Headers => lowercaseMap == other.lowercaseMap
    case _ => false
  }

  override def hashCode: Int = lowercaseMap.hashCode()

  override def toString: String = headers.toString()

  def toJson = EventLogJsonSupport.ToJson(toMap)

  def toBase64String: String = toJson.toBase64String

}

object Headers {

  def create(headers: (String, String)*) = new Headers(headers)

  def empty: Headers = create()

  def fromMap(map: Map[String, Set[String]]): Headers = {
    val headers = map.toList.flatMap {
      case (k, vs) => vs.map(v => (k, v))
    }

    Headers.create(headers: _*)
  }

}
