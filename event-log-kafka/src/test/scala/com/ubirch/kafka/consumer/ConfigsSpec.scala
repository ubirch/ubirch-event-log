package com.ubirch.kafka.consumer

import com.ubirch.TestBase
import org.apache.kafka.clients.consumer.ConsumerConfig

class ConfigsSpec extends TestBase {

  "Consumer Configs" must {

    "should contain basic keys " in {
      val configs = Configs("localhost: 9042", "my_group_id")

      val basicConfigs = List(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
        ConsumerConfig.GROUP_ID_CONFIG,
        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
        ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG,
        ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG,
        ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG,
        ConsumerConfig.MAX_POLL_RECORDS_CONFIG,
        ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG,
        ConsumerConfig.METADATA_MAX_AGE_CONFIG,
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
        ConsumerConfig.ISOLATION_LEVEL_CONFIG,
        ConsumerConfig.FETCH_MAX_BYTES_CONFIG
      )

      assert(configs.props.keys.toList.sortBy(x => x) == basicConfigs.sortBy(x => x))
    }

  }

}
