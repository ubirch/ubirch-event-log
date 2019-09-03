package com.ubirch.services.kafka.producer

import com.typesafe.config.Config
import com.ubirch.ConfPaths.ProducerConfPaths
import com.ubirch.kafka.producer.Configs
import com.ubirch.util.URLsHelper

trait ProducerCreator extends ProducerConfPaths {

  def config: Config

  def bootstrapServers: String = URLsHelper.passThruWithCheck(config.getString(BOOTSTRAP_SERVERS))

  def configs = Configs(bootstrapServers)

}
