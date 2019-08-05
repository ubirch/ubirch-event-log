package com.ubirch.kafka.util

import scala.collection.immutable.Map
import scala.language.implicitConversions

/**
  * Util to manage properties of type  Map[String, AnyRef].
  * It allows you to add a new property alone or add a new collection of properties.
  */
trait ConfigProperties {

  thiz =>

  val props: Map[String, AnyRef]

  def withProperty(key: String, value: AnyRef): ConfigProperties = {
    new ConfigProperties {
      override val props: Map[String, AnyRef] = {
        thiz.props + (key -> value)
      }
    }

  }

  def withConf(config: ConfigProperties): ConfigProperties = {
    new ConfigProperties {
      override val props: Map[String, AnyRef] = props ++ config.props
    }
  }

}

/**
  * Companion object for the ConfigProperties. Useful to have the implicit conversion handy.
  */
object ConfigProperties {
  implicit def configsToProps(configs: ConfigProperties): Map[String, AnyRef] = configs.props
}
