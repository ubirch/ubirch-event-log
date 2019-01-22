package com.ubirch.sdk.process

import com.ubirch.sdk.process.DefaultClientExecutors._
import org.json4s.JValue

trait EventLogger {
  self =>

  def commit: Unit

  def +>(other: EventLogger): EventLogger = new EventLogger {
    override def commit: Unit = {
      self.commit
      other.commit
    }
  }

  def <+(other: EventLogger): EventLogger = new EventLogger {
    override def commit: Unit = {
      other.commit
      self.commit
    }
  }

}

object EventLogger {

  def empty: EventLogger = new EventLogger {
    override def commit: Unit = ()
  }

  def apply(serviceClass: String, category: String, message: JValue): EventLogger = new EventLogger {
    override def commit: Unit = fromJValue(serviceClass, category)(message)
  }

  def apply[T: Manifest](serviceClass: String, category: String, message: T): EventLogger = new EventLogger {
    override def commit: Unit = {
      val f = from[T](serviceClass, category)
      f(message)
    }
  }

}

