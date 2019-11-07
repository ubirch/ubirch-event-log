package com.ubirch.services.kafka.producer

import com.typesafe.config.Config
import com.ubirch.ConfPaths.ProducerConfPaths
import com.ubirch.kafka.producer.ProducerBasicConfigs
import com.ubirch.util.URLsHelper

trait ProducerCreator extends ProducerBasicConfigs {

  def config: Config

  def lingerMs: Int = config.getInt(ProducerConfPaths.LINGER_MS)

  def producerBootstrapServers: String = URLsHelper.passThruWithCheck(config.getString(ProducerConfPaths.BOOTSTRAP_SERVERS))

}
