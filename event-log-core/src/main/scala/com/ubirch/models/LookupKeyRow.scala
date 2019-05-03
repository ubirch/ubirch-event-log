package com.ubirch.models

case class LookupKeyRow(category: String, key: String, value: String)

object LookupKeyRow {
  def fromLookUpKey(lookupKey: LookupKey): Seq[LookupKeyRow] = lookupKey.value.map(LookupKeyRow(lookupKey.category, lookupKey.key, _))
}

