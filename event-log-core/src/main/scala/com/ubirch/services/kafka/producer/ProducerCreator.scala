package com.ubirch.services.kafka.producer

import com.typesafe.config.Config
import com.ubirch.ConfPaths.ProducerConfPaths
import com.ubirch.kafka.producer.ProducerBasicConfigs
import com.ubirch.util.URLsHelper

trait ProducerCreator extends ProducerBasicConfigs with ProducerConfPaths {

  def config: Config

  def producerBootstrapServers: String = URLsHelper.passThruWithCheck(config.getString(BOOTSTRAP_SERVERS))

}
