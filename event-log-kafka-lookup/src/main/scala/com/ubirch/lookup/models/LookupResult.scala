package com.ubirch.lookup.models

import org.json4s.JsonAST.JValue

sealed trait LookupResult

case class NotFound(key: String) extends LookupResult

case class Found(key: String, event: JValue) extends LookupResult
