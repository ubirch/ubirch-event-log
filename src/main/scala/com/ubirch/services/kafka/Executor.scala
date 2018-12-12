package com.ubirch.services.kafka

import com.ubirch.Alias.ExecutorProcess
import com.ubirch.models.Events
import javax.inject._
import org.apache.kafka.clients.consumer.ConsumerRecords

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

class EventsStore @Inject() (events: Events) extends Executor[Vector[MessageEnvelope[String]], Unit] {
  override def apply(v1: Vector[MessageEnvelope[String]]): Unit = {
    println("Storing data...")
  }
}

class Logger extends Executor[Vector[MessageEnvelope[String]], Unit] {
  override def apply(v1: Vector[MessageEnvelope[String]]): Unit = println(v1)
}

trait ExecutorFamily {
  def wrapper: Wrapper
  def filterEmpty: FilterEmpty
  def logger: Logger
  def eventsStore: EventsStore
}

case class DefaultExecutorFamily @Inject() (
  wrapper: Wrapper,
  filterEmpty: FilterEmpty,
  logger: Logger,
  eventsStore: EventsStore) extends ExecutorFamily

class DefaultExecutor @Inject() (executorFamily: ExecutorFamily, events: Events) {

  import executorFamily._

  def executor: ExecutorProcess[Unit] =
    wrapper andThen filterEmpty andThen eventsStore

}
