package com.ubirch.verification

object ConfPaths {

  trait AcctEventPublishingConfPaths {
    final val LINGER_MS = "verification.acctEvent.kafkaProducer.lingerMS"
    final val BOOTSTRAP_SERVERS = "verification.acctEvent.kafkaProducer.bootstrapServers"
    final val TOPIC_PATH = "verification.acctEvent.kafkaProducer.topic"
    final val ERROR_TOPIC_PATH = "verification.acctEvent.kafkaProducer.errorTopic"
  }

  object AcctEventPublishingConfPaths extends AcctEventPublishingConfPaths

}
