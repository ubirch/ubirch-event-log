package com.ubirch.kafka.producer

import com.ubirch.kafka.util.ConfigProperties
import org.apache.kafka.clients.producer.ProducerConfig

/**
  * A convenience to manage the configuration keys that are used to
  * initialize the kafka producer.
  */
object Configs {

  def apply[K, V](
      bootstrapServers: String = "localhost:9092",
      acks: String = "all",
      retries: Int = 0,
      batchSize: Int = 16384,
      lingerMs: Int = 1,
      bufferMemory: Int = 33554432,
      enableIdempotence: Boolean = false,
      transactionalIdConfig: Option[String] = None
  ): ConfigProperties = {

    val configProperties = new ConfigProperties {
      override val props: Map[String, AnyRef] = Map[String, AnyRef](
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG -> bootstrapServers,
        ProducerConfig.ACKS_CONFIG -> acks,
        ProducerConfig.BATCH_SIZE_CONFIG -> batchSize.toString,
        ProducerConfig.LINGER_MS_CONFIG -> lingerMs.toString,
        ProducerConfig.BUFFER_MEMORY_CONFIG -> bufferMemory.toString,
        ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG -> enableIdempotence.toString
      )
    }
    val properties = if (retries != 0) {
      configProperties.withProperty(ProducerConfig.RETRIES_CONFIG, retries.toString)
    } else {
      configProperties
    }

    transactionalIdConfig
      .map(x =>
        properties.withProperty(ProducerConfig.TRANSACTIONAL_ID_CONFIG, x)).getOrElse(properties)

  }
}
