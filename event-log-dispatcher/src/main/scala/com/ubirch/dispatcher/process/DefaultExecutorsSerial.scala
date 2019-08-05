package com.ubirch.dispatcher.process

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.dispatcher.services.DispatchInfo
import com.ubirch.dispatcher.services.kafka.consumer.DispatcherPipeData
import com.ubirch.dispatcher.services.metrics.DefaultDispatchingCounter
import com.ubirch.dispatcher.util.Exceptions._
import com.ubirch.models.EventLog
import com.ubirch.process.{ BasicCommitUnit, Executor }
import com.ubirch.services.metrics.Counter
import com.ubirch.util._
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.{ ProducerRecord, RecordMetadata }
import org.json4s.JValue

import scala.concurrent.{ ExecutionContext, Future }
import scala.util.Try

@Singleton
class Dispatcher @Inject() (
    dispatchInfo: DispatchInfo,
    @Named(DefaultDispatchingCounter.name) counter: Counter,
    basicCommitter: BasicCommitUnit
) extends LazyLogging {

  def parse(consumerRecord: ConsumerRecord[String, String]) = {
    EventLogJsonSupport.FromString[EventLog](consumerRecord.value())
  }

  def createProducerRecords(eventLog: EventLog, eventLogJson: JValue): Vector[ProducerRecord[String, String]] = {

    import EventLogJsonSupport._

    dispatchInfo.info.find(_.category == eventLog.category)
      .map { y =>

        val commitDecisions = {

          import org.json4s._

          y.topics.map { t =>

            val dataToSend: String = t.dataToSend.filter(_.nonEmpty).flatMap { dts =>
              val dataFromEventLog = eventLogJson \ dts
              dataFromEventLog.extractOpt[String]
            }.orElse {
              val data = Option(eventLog.toJson)
              counter.counter.labels(t.name).inc()
              data
            }.getOrElse(throw DispatcherProducerRecordException("Empty Materials 2: No data field extracted.", eventLog.toJson))

            ProducerRecordHelper.toRecord(t.name, eventLog.id, dataToSend, Map.empty)

          }.toVector

        }

        commitDecisions
      }.getOrElse(throw DispatcherProducerRecordException("Empty Materials 1: No Dispatching Info", eventLog.toJson))

  }

  def publish(producerRecord: ProducerRecord[String, String]) =
    basicCommitter.send(producerRecord)
}

@Singleton
class Dispatch @Inject() (dispatcher: Dispatcher)(implicit ec: ExecutionContext)
  extends Executor[Vector[ConsumerRecord[String, String]], Future[DispatcherPipeData]]
  with LazyLogging {

  override def apply(v1: Vector[ConsumerRecord[String, String]]): Future[DispatcherPipeData] = Future {

    val pipeData = DispatcherPipeData.empty.withConsumerRecords(v1)

    val eventLogBuff = scala.collection.mutable.ListBuffer.empty[EventLog]
    val prsBuff = scala.collection.mutable.ListBuffer.empty[ProducerRecord[String, String]]
    val rmsBuff = scala.collection.mutable.ListBuffer.empty[RecordMetadata]

    if (v1.headOption.isEmpty) {
      throw EmptyValueException("No Records Found to be processed", pipeData)
    }

    v1.foreach { cr =>

      val (eventLog, eventLogJson) = Try {
        val fsEventLog = dispatcher.parse(cr)
        val eventLog = fsEventLog.get
        val eventLogJson = fsEventLog.json
        (eventLog, eventLogJson)
      }.getOrElse(throw ParsingIntoEventLogException("Error Parsing Event Log", pipeData))

      val prs = Try(dispatcher.createProducerRecords(eventLog, eventLogJson))
        .getOrElse(throw CreateProducerRecordException("Error Creating Producer Records", pipeData.withEventLogs(Vector(eventLog))))

      val rms = Try(prs.map(dispatcher.publish)).getOrElse(throw CommitException("Error publishing", pipeData.withProducerRecords(prs).withEventLogs(Vector(eventLog))))

      eventLogBuff += eventLog
      prsBuff ++= prs
      rmsBuff ++= rms

    }

    DispatcherPipeData(v1, eventLogBuff.toVector, prsBuff.toVector, rmsBuff.toVector)

  }

}
