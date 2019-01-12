package com.ubirch.util

trait ConfigProperties {

  val props: Map[String, AnyRef]

  def withProperty(key: String, value: AnyRef): ConfigProperties = {
    new ConfigProperties {
      override val props: Map[String, AnyRef] = props + (key -> value)
    }

  }

  def withConf(config: ConfigProperties): ConfigProperties = {
    new ConfigProperties {
      override val props: Map[String, AnyRef] = props ++ config.props
    }
  }

}
