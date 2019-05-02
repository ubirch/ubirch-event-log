package com.ubirch.models

case class LookupKeyRow(name: String, key: String, value: String)

object LookupKeyRow {
  def fromLookUpKey(lookupKey: LookupKey): Seq[LookupKeyRow] = lookupKey.value.map(LookupKeyRow(lookupKey.name, lookupKey.key, _))
}

