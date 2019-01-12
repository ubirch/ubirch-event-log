package com.ubirch

object ConfPaths {

  object CassandraCluster {
    final val CONTACT_POINTS = "eventLog.cassandra.cluster.contactPoints"
    final val PORT = "eventLog.cassandra.cluster.port"
    final val USERNAME = "eventLog.cassandra.cluster.username"
    final val PASSWORD = "eventLog.cassandra.cluster.password"
    final val KEYSPACE = "eventLog.cassandra.cluster.keyspace"
    final val PREPARED_STATEMENT_CACHE_SIZE = "eventLog.cassandra.cluster.preparedStatementCacheSize"
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
