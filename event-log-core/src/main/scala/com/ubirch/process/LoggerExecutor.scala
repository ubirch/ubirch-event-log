package com.ubirch.process

import com.datastax.driver.core.exceptions.{ InvalidQueryException, NoHostAvailableException }
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.EnrichedEventLog.enrichedEventLog
import com.ubirch.models.{ EventLog, EventsDAO, Values }
import com.ubirch.services.kafka.consumer.PipeData
import com.ubirch.util.EventLogJsonSupport
import com.ubirch.util.Exceptions.{ ParsingIntoEventLogException, StoringIntoEventLogException }
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecord

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class LoggerExecutor @Inject() (events: EventsDAO)(@Named("logger") implicit val ec: ExecutionContext)
  extends Executor[Vector[ConsumerRecord[String, String]], Future[PipeData]] with LazyLogging {

  def run(consumerRecord: ConsumerRecord[String, String]) = {

    Future(EventLogJsonSupport.FromString[EventLog](consumerRecord.value()).get.addTraceHeader(Values.EVENT_LOG_SYSTEM))
      .recover {
        case e: Exception =>
          throw ParsingIntoEventLogException("Error Parsing Into Event Log", PipeData(consumerRecord, None))
      }.flatMap { el =>
        val insertedRes = events.insertFromEventLog(el).recover {
          case e: NoHostAvailableException =>
            logger.error("Error connecting to host: " + e)
            throw e
          case e: InvalidQueryException =>
            logger.error("Error storing data (invalid query): " + e)
            throw e
          case e: Exception =>
            logger.error("Error storing data (other): " + e)
            throw StoringIntoEventLogException("Error storing data (other)", PipeData(consumerRecord, Some(el)), e.getMessage)
        }

        insertedRes
      }
  }

  override def apply(v1: Vector[ConsumerRecord[String, String]]): Future[PipeData] = Future {
    v1.map(run)
    PipeData(v1, None)
  }

}

