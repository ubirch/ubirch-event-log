package com.ubirch.services.config

import com.typesafe.config.{ Config, ConfigFactory }
import javax.inject._

/**
  * Configuration Provider for the Configuration Component.
  */
@Singleton
class ConfigProvider extends Provider[Config] {

  def conf: Config = ConfigFactory.load()

  override def get(): Config = conf

}
