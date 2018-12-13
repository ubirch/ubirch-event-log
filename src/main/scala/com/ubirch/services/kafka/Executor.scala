package com.ubirch.services.kafka

import com.ubirch.Alias.{EnvelopedEventLog, ExecutorProcessEnveloped, ExecutorProcessRaw, MessagesInEnvelope}
import com.ubirch.models.{EventLog, Events}
import com.ubirch.util.FromString
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecords

trait Executor[-T1, +R] extends (T1 ⇒ R) {
  self ⇒
  override def apply(v1: T1): R

  def andThen[Q](other: Executor[R, Q]): Executor[T1, Q] = {
    v1: T1 ⇒ other(self(v1))
  }

}

class Wrapper extends Executor[ConsumerRecords[String, String], MessagesInEnvelope[String]] {

  override def apply(v1: ConsumerRecords[String, String]): MessagesInEnvelope[String] = {
    val buffer = scala.collection.mutable.ListBuffer.empty[MessageEnvelope[String]]
    v1.iterator().forEachRemaining { record ⇒
      buffer += MessageEnvelope.fromRecord(record)
    }

    buffer.toVector

  }

}

class FilterEmpty extends Executor[MessagesInEnvelope[String], MessagesInEnvelope[String]] {

  override def apply(v1: MessagesInEnvelope[String]): MessagesInEnvelope[String] = {
    v1.filter(_.payload.nonEmpty).map(x ⇒ x.copy(payload = x.payload))
  }

}

class EventLogParser @Inject() (events: Events) extends ExecutorProcessEnveloped[MessagesInEnvelope[EventLog]] {

  override def apply(v1: MessagesInEnvelope[String]): MessagesInEnvelope[EventLog] = {
    val result: Vector[Option[MessageEnvelope[EventLog]]] =
      v1.map { m ⇒

        try {
          Option(m.copy(payload = FromString[EventLog](m.payload).get))
        } catch {
          case e: Exception ⇒
            e.printStackTrace()
            None
        }
      }

    //TODO: We need to use traverse here
    result.filter(_.isDefined).map(_.get)
  }
}

class EventsStore @Inject() (events: Events) extends ExecutorProcessEnveloped[Unit] {
  override def apply(v1: MessagesInEnvelope[String]): Unit = {
    println("Storing data...")
  }
}

class StringLogger extends ExecutorProcessEnveloped[Unit] {
  override def apply(v1: MessagesInEnvelope[String]): Unit = println(v1)
}

class EventLogLogger extends EnvelopedEventLog[Unit] {
  override def apply(v1: MessagesInEnvelope[EventLog]): Unit = println(v1)
}

trait ExecutorFamily {

  def wrapper: Wrapper

  def filterEmpty: FilterEmpty

  def stringLogger: StringLogger

  def eventLogLogger: EventLogLogger

  def eventsStore: EventsStore

  def eventLogParser: EventLogParser

}

case class DefaultExecutorFamily @Inject() (
  wrapper: Wrapper,
  filterEmpty: FilterEmpty,
  stringLogger: StringLogger,
  eventLogLogger: EventLogLogger,
  eventLogParser: EventLogParser,
  eventsStore: EventsStore) extends ExecutorFamily

class DefaultExecutor @Inject() (executorFamily: ExecutorFamily, events: Events) {

  import executorFamily._

  def executor: ExecutorProcessRaw[Unit] =
    wrapper andThen filterEmpty andThen eventLogParser andThen eventLogLogger

}
