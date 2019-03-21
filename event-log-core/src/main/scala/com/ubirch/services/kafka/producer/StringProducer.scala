package com.ubirch.services.kafka.producer

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.ConfPaths.ProducerConfPaths
import com.ubirch.kafka.producer.{ Configs, ProducerRunner }
import com.ubirch.services.lifeCycle.Lifecycle
import com.ubirch.util.URLsHelper
import javax.inject._
import org.apache.kafka.common.serialization.StringSerializer

import scala.concurrent.Future

/**
  * Class that represents a String Producer Factory
  */
class StringProducer extends ProducerRunner[String, String]

object StringProducer {
  def apply(props: Map[String, AnyRef], keySerializer: StringSerializer, valueSerializer: StringSerializer): StringProducer = {
    require(props.nonEmpty, "Can't be empty")
    val pd = new StringProducer
    pd.setProps(props)
    pd.setKeySerializer(Some(new StringSerializer()))
    pd.setValueSerializer(Some(new StringSerializer()))
    pd
  }
}

/**
  * Class that represents a String Producer Factory with specific values from the config files
  * @param config Config Component for reading config properties
  * @param lifecycle LifeCycle Component Instance for adding the producer stop hook
  */
@Singleton
class DefaultStringProducer @Inject() (config: Config, lifecycle: Lifecycle) extends Provider[StringProducer] with ProducerConfPaths with LazyLogging {

  val bootstrapServers: String = URLsHelper.passThruWithCheck(config.getString(BOOTSTRAP_SERVERS))

  def configs = Configs(bootstrapServers)

  private lazy val producerConfigured = StringProducer(configs, new StringSerializer(), new StringSerializer())

  override def get(): StringProducer = producerConfigured

  lifecycle.addStopHook { () =>
    logger.info("Shutting down Producer...")

    get().getProducerAsOpt.map { prod =>
      Future.successful(prod.close())
    }.getOrElse {
      Future.unit
    }

  }

}
