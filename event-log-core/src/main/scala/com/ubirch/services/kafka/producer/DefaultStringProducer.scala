package com.ubirch.services.kafka.producer

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ProducerConfPaths
import com.ubirch.kafka.producer.{ Configs, StringProducer, WithProducerShutdownHook }
import com.ubirch.services.lifeCycle.Lifecycle
import com.ubirch.util.URLsHelper
import javax.inject._

/**
  * Class that represents a String Producer Factory with specific values from the config files
  * @param config Represents the properties to start the producer.
  * @param lifecycle LifeCycle Component Instance for adding the producer stop hook
  */
@Singleton
class DefaultStringProducer @Inject() (
    config: Config,
    lifecycle: Lifecycle
) extends Provider[StringProducer] with LazyLogging with ProducerConfPaths with WithProducerShutdownHook {

  def bootstrapServers: String = URLsHelper.passThruWithCheck(config.getString(BOOTSTRAP_SERVERS))

  def lingerMs: Int = config.getInt(LINGER_MS)

  def configs = Configs(bootstrapServers, lingerMs = lingerMs)

  private lazy val producerConfigured = StringProducer(configs)

  override def get(): StringProducer = producerConfigured

  lifecycle.addStopHook(hookFunc(get()))

}
