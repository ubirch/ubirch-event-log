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

object ServiceShortestPath extends Boot(LookupServiceBinder.modules) {

  def main(args: Array[String]): Unit = {

    implicit val ec = get[ExecutionContext]

    val gremlin = get[GremlinFinder]
    def shortestPath = gremlin.shortestPath(
      Values.HASH,
      "AL1p+p253eQqC+PoJuhSBCg/2Hh4MwfGyPFkcnvTmWIDJc7fA9q14Tca+Bjf7VouclApwedQXUtaMj4Ipy7Iuw==",
      Values.PUBLIC_CHAIN_CATEGORY
    )

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
