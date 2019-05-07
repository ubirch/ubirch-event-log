package com.ubirch.dispatcher.models

case class DispatchTopic(name: String, dataToSend: Option[String])
case class Dispatch(category: String, topics: Seq[DispatchTopic])
