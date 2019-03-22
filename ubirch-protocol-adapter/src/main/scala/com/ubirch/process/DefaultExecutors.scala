package com.ubirch.process

import com.ubirch.kafka.MessageEnvelope
import com.ubirch.sdk.EventLoggingBase
import com.ubirch.services.AdapterServiceBinder
import com.ubirch.services.kafka.consumer.MessageEnvelopePipeData
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.concurrent.Future

@Singleton
class EventLoggerExecutor @Inject() (eventLogger: EventLogger) extends Executor[ConsumerRecord[String, MessageEnvelope], Future[MessageEnvelopePipeData]] {
  override def apply(v1: ConsumerRecord[String, MessageEnvelope]): Future[MessageEnvelopePipeData] = {
    import eventLogger._
    val eventLog = eventLogger.log(v1.value(), "Ubirch-EventLog-Adaptor", "UPA")
    eventLog.commitAsync.map { el =>
      MessageEnvelopePipeData(v1, Some(el))
    }
  }
}

@Singleton
class EventLogger extends EventLoggingBase(AdapterServiceBinder.modules)

trait ExecutorFamily {

  def eventLoggerExecutor: EventLoggerExecutor

}

@Singleton
class DefaultExecutorFamily(val eventLoggerExecutor: EventLoggerExecutor) extends ExecutorFamily
