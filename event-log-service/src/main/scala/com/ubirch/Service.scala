package com.ubirch

import com.ubirch.services.kafka.consumer.{ ConsumerImp, ConsumerRecordsController }
import com.ubirch.util.Boot
import org.apache.kafka.clients.consumer.ConsumerRecords

/**
  * Represents an Event Log Service.
  * It starts an String Consumer that in turn starts all the rest of the
  * needed components, such as all the core components, executors, reporters, etc.
  */
object Service extends Boot {

  def main(args: Array[String]): Unit = {

    val consumer = get[ConsumerImp]

    consumer.setConsumerRecordsController(
      Some(
        new ConsumerRecordsController[String, String] {
          override def isValueEmpty(v: String): Boolean = v.isEmpty

          override def process(consumerRecords: ConsumerRecords[String, String]): Unit = {
            val ite = consumerRecords.iterator()
            while (ite.hasNext) {
              println(ite.next().value())
            }

          }
        }
      )
    )

    consumer.start()

  }

}
