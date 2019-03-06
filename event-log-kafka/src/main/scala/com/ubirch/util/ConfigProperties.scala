package com.ubirch.util

import scala.collection.immutable.Map
import scala.language.implicitConversions

/**
  * Util to manage properties of type  Map[String, AnyRef].
  * It allows you to add a new property alone or add a new collection of properties.
  */
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

object ConfigProperties {
  implicit def configsToProps(configs: ConfigProperties): Map[String, AnyRef] = configs.props
}
