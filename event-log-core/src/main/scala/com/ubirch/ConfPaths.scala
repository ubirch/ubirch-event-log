package com.ubirch

/**
  * Object that contains configuration keys
  */
object ConfPaths {

  trait ExecutionContextConfPaths {
    val THREAD_POOL_SIZE = "eventLog.executionContext.threadPoolSize"
  }

  trait CassandraClusterConfPaths {
    val CONTACT_POINTS = "eventLog.cassandra.cluster.contactPoints"
    val CONSISTENCY_LEVEL = "eventLog.cassandra.cluster.consistencyLevel"
    val SERIAL_CONSISTENCY_LEVEL = "eventLog.cassandra.cluster.serialConsistencyLevel"
    val WITH_SSL = "eventLog.cassandra.cluster.withSSL"
    val USERNAME = "eventLog.cassandra.cluster.username"
    val PASSWORD = "eventLog.cassandra.cluster.password"
    val KEYSPACE = "eventLog.cassandra.cluster.keyspace"
    val PREPARED_STATEMENT_CACHE_SIZE = "eventLog.cassandra.cluster.preparedStatementCacheSize"
  }

  trait ConsumerConfPaths {
    val BOOTSTRAP_SERVERS = "eventLog.kafkaConsumer.bootstrapServers"
    val TOPIC_PATH = "eventLog.kafkaConsumer.topic"
    val MAX_POLL_RECORDS = "eventLog.kafkaConsumer.maxPollRecords"
    val GROUP_ID_PATH = "eventLog.kafkaConsumer.groupId"
    val GRACEFUL_TIMEOUT_PATH = "eventLog.kafkaConsumer.gracefulTimeout"
    val METRICS_SUB_NAMESPACE = "eventLog.kafkaConsumer.metricsSubNamespace"
    val FETCH_MAX_BYTES_CONFIG = "eventLog.kafkaConsumer.fetchMaxBytesConfig"
    val MAX_PARTITION_FETCH_BYTES_CONFIG = "eventLog.kafkaConsumer.maxPartitionFetchBytesConfig"
  }

  trait ProducerConfPaths {
    val LINGER_MS = "eventLog.kafkaProducer.lingerMS"
    val BOOTSTRAP_SERVERS = "eventLog.kafkaProducer.bootstrapServers"
    val ERROR_TOPIC_PATH = "eventLog.kafkaProducer.errorTopic"
    val TOPIC_PATH = "eventLog.kafkaProducer.topic"
  }

  trait PrometheusConfPaths {
    val PORT = "eventLog.metrics.prometheus.port"
  }

  trait CryptoConfPaths {
    val SERVICE_PK = "crypto.keys.ed25519.signingPrivateKey"
  }

  object ConsumerConfPaths extends ConsumerConfPaths
  object ProducerConfPaths extends ProducerConfPaths

}
