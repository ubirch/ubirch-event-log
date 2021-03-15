package com.ubirch.verification

object ConfPaths {

  trait AcctEventPublishingConfPaths {
    final val LINGER_MS = "verification.acctEvent.kafkaProducer.lingerMS"
    final val BOOTSTRAP_SERVERS = "verification.acctEvent.kafkaProducer.bootstrapServers"
    final val TOPIC_PATH = "verification.acctEvent.kafkaProducer.topic"
    final val ERROR_TOPIC_PATH = "verification.acctEvent.kafkaProducer.errorTopic"
  }

  trait RedisConfPaths {
    final val REDIS_PORT = "verification.redis.port"
    final val REDIS_PASSWORD = "verification.redis.password"
    final val REDIS_USE_REPLICATED = "verification.redis.useReplicated"
    final val REDIS_INDEX = "verification.redis.index"
    final val REDIS_CACHE_TTL = "verification.redis.ttl"
    final val REDIS_MAIN_HOST = "verification.redis.mainHost"
    final val REDIS_REPLICATED_HOST = "verification.redis.replicatedHost"
    final val REDIS_HOST = "verification.redis.host"
  }

  object AcctEventPublishingConfPaths extends AcctEventPublishingConfPaths

  object RedisConfPaths extends RedisConfPaths

}
