package com.ubirch.services.config

import com.typesafe.config.{ Config, ConfigFactory }
import javax.inject._

/**
  * Configuration Provider for the Configuration Component.
  */
@Singleton
class ConfigProvider extends Provider[Config] {

  val default = ConfigFactory.load()

  def conf: Config = default

  override def get(): Config = conf

}
