package com.ubirch.services.kafka.consumer

import org.apache.kafka.clients.consumer.ConsumerConfig

trait ConfigBaseHelpers {

  private var _props: Map[String, AnyRef] = Map.empty

  def props = _props

  def withProps(ps: Map[String, AnyRef]): this.type = {
    _props = ps
    this
  }

  private var _topic: String = ""

  def topic = _topic

  def withTopic(value: String): this.type = {
    _topic = value
    this
  }

  def isAutoCommit: Boolean = props
    .filterKeys(x ⇒ ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG == x)
    .exists {
      case (_, b) ⇒
        b.toString.toBoolean
    }

}
