package com.ubirch.verification

object ConfPaths {

  trait AcctEventPublishingConfPaths {
    final val LINGER_MS = "identitySystem.wolf.kafkaProducer.lingerMS"
    final val BOOTSTRAP_SERVERS = "identitySystem.wolf.kafkaProducer.bootstrapServers"
    final val TOPIC_PATH = "identitySystem.wolf.kafkaProducer.topic"
    final val ERROR_TOPIC_PATH = "identitySystem.wolf.kafkaProducer.errorTopic"
  }

  object AcctEventPublishingConfPaths extends AcctEventPublishingConfPaths

}
