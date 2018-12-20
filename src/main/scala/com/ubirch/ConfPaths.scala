package com.ubirch

object ConfPaths {

  object ConnectionService {
    final val KEYSPACE = "eventLog.cluster.keyspace"
    final val PREPARED_STATEMENT_CACHE_SIZE = "eventLog.cluster.preparedStatementCacheSize"
  }

  object Consumer {
    final val BOOTSTRAP_SERVERS = "eventLog.kafkaConsumer.bootstrapServers"
    final val TOPIC_PATH = "eventLog.kafkaConsumer.topic"
    final val ERROR_TOPIC_PATH = "eventLog.kafkaConsumer.errorTopic"
    final val GROUP_ID_PATH = "eventLog.kafkaConsumer.groupId"
    final val GRACEFUL_TIMEOUT_PATH = "eventLog.kafkaConsumer.gracefulTimeout"
  }

  object Producer {
    final val BOOTSTRAP_SERVERS = "eventLog.kafkaProducer.bootstrapServers"
    final val ERROR_TOPIC_PATH = "eventLog.kafkaProducer.errorTopic"
  }

}
