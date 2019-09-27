package com.ubirch.dispatcher.models

case class DispatchTopic(tags: List[String], name: String, dataToSend: Option[String])
case class Dispatch(category: String, topics: Seq[DispatchTopic])
