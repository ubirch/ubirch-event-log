package com.ubirch.models

case class Key(name: String, label: Option[String]) {
  def withLabel(newLabel: String): Key = copy(label = Option(newLabel))
}

case class Value(name: String, label: Option[String], extra: Map[String, String] = Map.empty) {
  def withLabel(newLabel: String): Value = copy(label = Option(newLabel))
  def withExtra(newExtra: Map[String, String]): Value = copy(extra = newExtra)
  def addExtra(extras: (String, String)*): Value = copy(extra = extra ++ extras.toMap[String, String])
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

  implicit class Helpers(name: String) {
    def asKey: Key = Key(name, None)
    def asKeyWithLabel(label: String): Key = Key(name, Option(label))
    def asValue: Value = Value(name, None, Map.empty)
    def asValueWithLabel(label: String): Value = Value(name, Option(label), Map.empty)
  }

}

