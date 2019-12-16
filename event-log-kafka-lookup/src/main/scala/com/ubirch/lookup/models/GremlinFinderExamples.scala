package com.ubirch.lookup.models

import java.util.Date

import com.ubirch.lookup.services.LookupServiceBinder
import com.ubirch.util.Boot

import scala.concurrent.ExecutionContext
import scala.language.postfixOps
import scala.util.{ Failure, Success }
import com.ubirch.models.Values

object FindAnchorsWithPathAsVertices extends Boot(LookupServiceBinder.modules) {

  def main(args: Array[String]): Unit = {

    implicit val ec = get[ExecutionContext]

    val gremlin = get[GremlinFinder]

    val res = gremlin.findAnchorsWithPathAsVertices("M66G3UAGFRSHhJ0hKcfFX0INsGFhvvzI5QMolT331XGGBaKB7rH6xqh4yBZUQmRQsRS4yVcHIRZNegJCY6fgFg==")

    res.onComplete {
      case Success((a, b)) =>
        println("shortestPath: " + a.toString())
        println("upper: " + b.toString())
      case Failure(exception) =>
        logger.info("Hola")
        throw exception
    }

  }

}

object FindUpperAndLower extends Boot(LookupServiceBinder.modules) {

  def main(args: Array[String]): Unit = {

    implicit val ec = get[ExecutionContext]

    val gremlin = get[GremlinFinder]

    val res = gremlin.findUpperAndLower("88gHo6x2R9IujZP7y0hMAjBQfQ9mpIDcVuRvV6bynP+YYqoANg7n8V/ZbbhQxCWBCh/UGqzFqMoaTf075rtJRw==")

    val t = for {
      (_sp, _u, _lp, _l) <- res
      sx <- gremlin.toVertexStruct(_sp)
      ux <- gremlin.toVertexStruct(_u)
      lp <- gremlin.toVertexStruct(_lp)
      lw <- gremlin.toVertexStruct(_l)
    } yield {
      (sx, ux, lp, lw)
    }

    t.onComplete {
      case Success((a, b, c, d)) =>
        println("shortestPath: " + a.map(x => (x.label, x.get(Values.TIMESTAMP).map(y => new Date(y.toString.toLong)), x.get(Values.HASH).map(_.toString).getOrElse(""))).mkString("\n"))
        println("upper: " + b.map(x => (x.label, x.get(Values.TIMESTAMP).map(y => new Date(y.toString.toLong)), x.get(Values.HASH).map(_.toString).getOrElse(""))).mkString("\n"))
        println("lower-path: " + c.map(x => (x.label, x.get(Values.TIMESTAMP).map(y => new Date(y.toString.toLong)), x.get(Values.HASH).map(_.toString).getOrElse(""))).mkString("\n"))
        println("lower: " + d.map(x => (x.label, x.get(Values.TIMESTAMP).map(y => new Date(y.toString.toLong)), x.get(Values.HASH).map(_.toString).getOrElse(""))).mkString("\n"))
      case Failure(exception) =>
        logger.info("Hola")
        throw exception
    }

  }

}

object FindUpperAndLowerAsVertices extends Boot(LookupServiceBinder.modules) {

  def main(args: Array[String]): Unit = {

    implicit val ec = get[ExecutionContext]

    val gremlin = get[GremlinFinder]

    val res = gremlin.findUpperAndLowerAsVertices("M66G3UAGFRSHhJ0hKcfFX0INsGFhvvzI5QMolT331XGGBaKB7rH6xqh4yBZUQmRQsRS4yVcHIRZNegJCY6fgFg==")

    val t = res
    t.onComplete {
      case Success((a, b, c, d)) =>
        println("shortestPath: " + a.mkString("\n"))
        println("upper: " + b.mkString("\n"))
        println("lower-path: " + c.mkString("\n"))
        println("lower: " + d.mkString("\n"))
      case Failure(exception) =>
        logger.info("Hola")
        throw exception
    }

  }

}