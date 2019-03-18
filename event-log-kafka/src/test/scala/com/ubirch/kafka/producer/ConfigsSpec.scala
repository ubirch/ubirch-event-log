package com.ubirch.kafka.producer

import com.ubirch.TestBase
import org.apache.kafka.clients.producer.ProducerConfig

class ConfigsSpec extends TestBase {

  "Producer Configs" must {

    "should contain basic keys " in {
      val configs = Configs("localhost: 9042", "my_group_id")

      val basicConfigs = List(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
        ProducerConfig.ACKS_CONFIG,
        ProducerConfig.BATCH_SIZE_CONFIG,
        ProducerConfig.LINGER_MS_CONFIG,
        ProducerConfig.BUFFER_MEMORY_CONFIG,
        ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG
      )

      assert(configs.props.keys.toList.sortBy(x => x) == basicConfigs.sortBy(x => x))
    }

  }

}
