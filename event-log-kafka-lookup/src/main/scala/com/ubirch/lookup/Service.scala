package com.ubirch.lookup

import com.ubirch.kafka.consumer.StringConsumer
import com.ubirch.lookup.models.GremlinFinder
import com.ubirch.lookup.services.LookupServiceBinder
import com.ubirch.models.Values
import com.ubirch.util.Boot

import scala.concurrent.ExecutionContext
import scala.language.postfixOps
import scala.util.{ Failure, Success }

/**
  * Represents an the Kafka Lookup boot object.
  */
object Service extends Boot(LookupServiceBinder.modules) {

  def main(args: Array[String]): Unit = {

    val consumer = get[StringConsumer]

    consumer.start()

  }

}

object Service2 extends Boot(LookupServiceBinder.modules) {

  def main(args: Array[String]): Unit = {

    implicit val ec = get[ExecutionContext]

    val gremlin = get[GremlinFinder]
    def shortestPath = gremlin.shortestPath(Values.HASH, "QT5Q9VQXGbiF3TKhdTJkXs4LLXBp90DU26ZX2TLxp2HDjEtgO1OPsIg0N8Flfx5eSJbookutNWlOVZGTwBQfsw==", Values.PUBLIC_CHAIN_CATEGORY)

    val t = for {
      sp <- shortestPath
      vx <- gremlin.toVertexStruct(sp)
    } yield {
      vx
    }

    t.onComplete {
      case Success(value) =>
        println(value)
      case Failure(exception) =>
        throw exception
    }

  }

}

