package com.ubirch.chainer

import com.typesafe.config.Config
import com.ubirch.ConfPaths.ProducerConfPaths
import com.ubirch.chainer.services.ChainerServiceBinder
import com.ubirch.kafka.consumer.{ All, StringConsumer }
import com.ubirch.kafka.producer.{ Configs, ProducerRunner }
import com.ubirch.models.EventLog
import com.ubirch.util.{ Boot, URLsHelper }
import kafka.admin.AdminUtils
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{ Deserializer, Serializer, StringSerializer }
import org.json4s.JsonAST.JInt

import scala.language.postfixOps

/**
  * Represents an EventLog Chainer Service.
  */

object Service extends Boot(ChainerServiceBinder.modules) {

  def main(args: Array[String]): Unit = {

    val consumer: StringConsumer = get[StringConsumer]

    consumer.setConsumptionStrategy(All)

    consumer.start()

  }

}

object ServiceTest extends Boot(ChainerServiceBinder.modules) with ProducerConfPaths {

  val config = get[Config]

  def bootstrapServers: String = URLsHelper.passThruWithCheck(config.getString(BOOTSTRAP_SERVERS))

  def lingerMs: Int = config.getInt(LINGER_MS)

  def main(args: Array[String]): Unit = {
    def configs = Configs(bootstrapServers, lingerMs = lingerMs)

    val producer = ProducerRunner[String, String](configs, Some(new StringSerializer()), Some(new StringSerializer()))

    val range = (0 to 100000)

    try {

      range.map { x =>
        val entity = EventLog(JInt(x)).toJson
        producer.getProducerOrCreate.send(new ProducerRecord[String, String]("com.ubirch.eventlog", entity))
      }
    } finally {
      producer.getProducerOrCreate.close()
      System.exit(0)

    }

  }

}
