package com.ubirch.chainer

import com.typesafe.config.Config
import com.ubirch.ConfPaths.ProducerConfPaths
import com.ubirch.chainer.models.{ Master, Mode, Slave }
import com.ubirch.chainer.services.ChainerServiceBinder
import com.ubirch.chainer.services.tree.TreeMonitor
import com.ubirch.chainer.util.{ ChainerJsonSupport, PMHelper }
import com.ubirch.kafka.consumer.{ All, StringConsumer }
import com.ubirch.kafka.producer.{ Configs, ProducerRunner }
import com.ubirch.models.{ EventLog, Values }
import com.ubirch.protocol.ProtocolMessage
import com.ubirch.util.{ Boot, URLsHelper }
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.json4s.JsonAST.JInt

import scala.language.postfixOps

/**
  * Represents an EventLog Chainer Service.
  */

object Service extends Boot(ChainerServiceBinder.modules) {

  def main(args: Array[String]): Unit = {

    val consumer: StringConsumer = get[StringConsumer]
    get[TreeMonitor]

    consumer.setConsumptionStrategy(All)

    consumer.start()

  }

}

object ServiceTest extends Boot(ChainerServiceBinder.modules) with ProducerConfPaths {

  val config = get[Config]

  def bootstrapServers: String = URLsHelper.passThruWithCheck(config.getString(BOOTSTRAP_SERVERS))

  def lingerMs: Int = config.getInt(LINGER_MS)

  def modeFromConfig: String = config.getString("eventLog.mode")

  def mode: Mode = Mode.getMode(modeFromConfig)

  def main(args: Array[String]): Unit = {
    def configs = Configs(bootstrapServers, lingerMs = lingerMs)

    val producer = ProducerRunner[String, String](configs, Some(new StringSerializer()), Some(new StringSerializer()))

    val range = (0 to 10000)

    def data(index: Int) = mode match {
      case Slave =>
        val d = ChainerJsonSupport.ToJson[ProtocolMessage](PMHelper.createPM).get
        EventLog(d).withNewId.withCurrentEventTime.withRandomNonce.withCategory(Values.UPP_CATEGORY).toJson
      case Master =>
        val d = JInt(index)
        EventLog(d).withNewId.withCurrentEventTime.withRandomNonce.withCategory(Values.SLAVE_TREE_CATEGORY).toJson
    }

    try {

      range.map { entity =>
        producer.getProducerOrCreate.send(new ProducerRecord[String, String]("com.ubirch.chainer.slave", data(entity)))
      }

    } finally {
      producer.getProducerOrCreate.close()
      System.exit(0)

    }

  }

}
