package com.ubirch.models

case class Key(name: String, label: Option[String]) {
  def withLabel(newLabel: String): Key = copy(label = Option(newLabel))
}

case class Value(name: String, label: Option[String]) {
  def withLabel(newLabel: String): Value = copy(label = Option(newLabel))
}

case class LookupKey(name: String, category: String, key: Key, value: Seq[Value]) {
  def withKey(newKey: Key): LookupKey = copy(key = newKey)
  def withValue(newValue: Seq[Value]): LookupKey = copy(value = newValue)
  def addValue(newValue: Value): LookupKey = copy(value = value ++ Seq(newValue))
  def withKeyLabel(newLabel: String): LookupKey = copy(key = key.withLabel(newLabel))
  def addValueLabelForAll(newLabel: String): LookupKey = copy(value = value.map(_.withLabel(newLabel)))

  def categoryAsKeyLabel: LookupKey = withKeyLabel(category)
  def nameAsValueLabelForAll: LookupKey = addValueLabelForAll(name)

  def categoryAsValueLabelForAll: LookupKey = addValueLabelForAll(category)
  def nameAsKeyLabel: LookupKey = withKeyLabel(name)

}

object LookupKey {
  def apply(name: String, category: String, key: String, value: Seq[String]): LookupKey = {
    new LookupKey(name, category, Key(key, None), value.map(Value(_, None)))
  }

  def apply(name: String, category: String, key: (String, String), value: Seq[(String, String)]): LookupKey = {
    new LookupKey(name, category, Key(key._1, Some(key._2)), value.map(x => Value(x._1, Some(x._2))))
  }

}

