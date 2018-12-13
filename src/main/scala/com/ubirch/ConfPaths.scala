package com.ubirch

object ConfPaths {

  final val KEYSPACE = "eventLog.cluster.keyspace"
  final val PREPARED_STATEMENT_CACHE_SIZE = "eventLog.cluster.preparedStatementCacheSize"

  final val TOPIC_PATH = "eventLog.kafkaConsumer.topic"
  final val GROUP_ID_PATH = "eventLog.kafkaConsumer.groupId"
  final val GRACEFUL_TIMEOUT_PATH = "eventLog.kafkaConsumer.gracefulTimeout"

}
