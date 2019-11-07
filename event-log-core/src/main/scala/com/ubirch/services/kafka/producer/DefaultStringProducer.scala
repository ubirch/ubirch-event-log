package com.ubirch.services.kafka.producer

import com.typesafe.config.Config
import com.ubirch.kafka.producer.{ StringProducer, WithProducerShutdownHook }
import com.ubirch.services.lifeCycle.Lifecycle
import javax.inject._

/**
  * Class that represents a String Producer Factory with specific values from the config files
  * @param config Represents the properties to start the producer.
  * @param lifecycle LifeCycle Component Instance for adding the producer stop hook
  */
@Singleton
class DefaultStringProducer @Inject() (
    val config: Config,
    lifecycle: Lifecycle
) extends Provider[StringProducer]
  with ProducerCreator
  with WithProducerShutdownHook {

  private lazy val producerConfigured = StringProducer(producerConfigs)

  override def get(): StringProducer = producerConfigured

  lifecycle.addStopHook(hookFunc(get()))

}
