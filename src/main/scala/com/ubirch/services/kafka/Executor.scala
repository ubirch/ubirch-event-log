package com.ubirch.services.kafka

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.models.{ EventLog, Events }
import com.ubirch.util.FromString
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecords

import scala.concurrent.{ ExecutionContext, Future }

trait Executor[-T1, +R] extends (T1 ⇒ R) {
  self ⇒
  override def apply(v1: T1): R

  def andThen[Q](other: Executor[R, Q]): Executor[T1, Q] = {
    v1: T1 ⇒ other(self(v1))
  }

}

class Wrapper extends Executor[ConsumerRecords[String, String], Vector[MessageEnvelope[String]]] {

  override def apply(v1: ConsumerRecords[String, String]): Vector[MessageEnvelope[String]] = {

    val buffer = scala.collection.mutable.ListBuffer.empty[MessageEnvelope[String]]
    v1.iterator().forEachRemaining { record ⇒
      buffer += MessageEnvelope.fromRecord(record)
    }

    buffer.toVector

  }

}

class FilterEmpty extends Executor[Vector[MessageEnvelope[String]], Vector[MessageEnvelope[String]]] {

  override def apply(v1: Vector[MessageEnvelope[String]]): Vector[MessageEnvelope[String]] = {

    v1.filter(_.payload.nonEmpty).map(x ⇒ x.copy(payload = x.payload))

  }

}

class EventLogParser
    extends Executor[Vector[MessageEnvelope[String]], Vector[MessageEnvelope[EventLog]]]
    with LazyLogging {

  override def apply(v1: Vector[MessageEnvelope[String]]): Vector[MessageEnvelope[EventLog]] = {

    val result: Vector[Option[MessageEnvelope[EventLog]]] =
      v1.map { m ⇒

        try {
          Option(m.copy(payload = FromString[EventLog](m.payload).get))
        } catch {
          case e: Exception ⇒
            logger.error("Error Parsing Event: " + e.getMessage)
            None
        }
      }

    result
      .filter(_.isDefined)
      .flatMap(x ⇒ Option(x.get))
  }
}

class EventsStore @Inject() (events: Events)(implicit ec: ExecutionContext)
    extends Executor[Vector[MessageEnvelope[EventLog]], Future[Vector[Unit]]] {

  override def apply(v1: Vector[MessageEnvelope[EventLog]]): Future[Vector[Unit]] = {

    val vectorOfFutureResults = v1.map { m ⇒
      events.insert(m.payload)
    }

    Future.sequence(vectorOfFutureResults)

  }
}

trait ExecutorFamily {

  def wrapper: Wrapper

  def filterEmpty: FilterEmpty

  def eventsStore: EventsStore

  def eventLogParser: EventLogParser

}

case class DefaultExecutorFamily @Inject() (
  wrapper: Wrapper,
  filterEmpty: FilterEmpty,
  eventLogParser: EventLogParser,
  eventsStore: EventsStore) extends ExecutorFamily

class DefaultExecutor @Inject() (executorFamily: ExecutorFamily, events: Events) {

  import executorFamily._

  def composed = wrapper andThen filterEmpty andThen eventLogParser andThen eventsStore

  def executor = composed

}
