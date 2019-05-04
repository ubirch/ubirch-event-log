package com.ubirch.models

case class LookupKeyRow(name: String, category: String, key: String, value: String)

object LookupKeyRow {
  def fromLookUpKey(lookupKey: LookupKey): Seq[LookupKeyRow] = {
    lookupKey.value.map(LookupKeyRow(lookupKey.name, lookupKey.category, lookupKey.key, _))
  }
}

