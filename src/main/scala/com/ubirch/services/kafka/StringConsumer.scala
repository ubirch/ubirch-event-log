package com.ubirch.services.kafka

import org.apache.kafka.clients.consumer.ConsumerRecords

class StringConsumer[R](
  val topic: String,
  configs: Configs,
  name: String,
  val maybeExecutor: Option[Executor[ConsumerRecords[String, String], R]])
  extends AbstractStringConsumer[R](name) {

  override val props: Map[String, AnyRef] = configs.props

}

