package com.ubirch.verification.service.services

import java.text.SimpleDateFormat

import com.ubirch.models.Values
import com.ubirch.util.Boot
import com.ubirch.verification.service.LookupServiceBinder
import com.ubirch.verification.service.models.VertexStruct

import scala.concurrent.{ ExecutionContext, Future }
import scala.language.postfixOps
import scala.util.{ Failure, Success }

object FindAnchorsWithPathAsVertices extends Boot(LookupServiceBinder.modules) {

  def main(args: Array[String]): Unit = {

    implicit val ec: ExecutionContext = get[ExecutionContext]

    val gremlin = get[GremlinFinder]
    val t0 = System.currentTimeMillis()
    val res = gremlin.findAnchorsWithPathAsVertices("hCh65wOknIAFzs/QCLpoOF1gvNYnNv26Hn3bcfXqzqCwfpwGlDgPVvurtOyvmcTx5Y1Y4G7MhVxyaXgAzVjh0A==")
    val t1 = System.currentTimeMillis()
    res.onComplete {
      case Success((a, b)) =>
        println("shortestPath: " + a.toString())
        println("upper: " + b.toString())
        println(s"FindAnchorsWithPathAsVertices finished. Time to complete: ${t1 - t0}ms")
      case Failure(exception) =>
        logger.info("Hola")
        throw exception
    }

  }

}

object FindUpperAndLower extends Boot(LookupServiceBinder.modules) {

  def main(args: Array[String]): Unit = {

    implicit val ec: ExecutionContext = get[ExecutionContext]

    val gremlin = get[GremlinFinder]

    val res: Future[(List[VertexStruct], List[VertexStruct], List[VertexStruct], List[VertexStruct])] = gremlin.findUpperAndLower("88gHo6x2R9IujZP7y0hMAjBQfQ9mpIDcVuRvV6bynP+YYqoANg7n8V/ZbbhQxCWBCh/UGqzFqMoaTf075rtJRw==")

    val t = for {
      (_sp, _u, _lp, _l) <- res
    } yield {
      (_sp, _u, _lp, _l)
    }
    val timeFormatter = new SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy")

    t.onComplete {
      case Success((a, b, c, d)) =>
        println("shortestPath: " + a.map(x => (x.label, x.get(Values.TIMESTAMP).map(y => timeFormatter.parse(y.toString)), x.get(Values.HASH).map(_.toString).getOrElse(""))).mkString("\n"))
        println("upper: " + b.map(x => (x.label, x.get(Values.TIMESTAMP).map(y => timeFormatter.parse(y.toString)), x.get(Values.HASH).map(_.toString).getOrElse(""))).mkString("\n"))
        println("lower-path: " + c.map(x => (x.label, x.get(Values.TIMESTAMP).map(y => timeFormatter.parse(y.toString)), x.get(Values.HASH).map(_.toString).getOrElse(""))).mkString("\n"))
        println("lower: " + d.map(x => (x.label, x.get(Values.TIMESTAMP).map(y => timeFormatter.parse(y.toString)), x.get(Values.HASH).map(_.toString).getOrElse(""))).mkString("\n"))
      case Failure(exception) =>
        logger.info("Hola")
        throw exception
    }

  }

}

object FindUpperAndLowerAsVertices extends Boot(LookupServiceBinder.modules) {

  def main(args: Array[String]): Unit = {

    implicit val ec: ExecutionContext = get[ExecutionContext]

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
