package com.ubirch.sdk

import java.util.concurrent.CountDownLatch

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.util.Implicits.enrichedInstant
import com.ubirch.util.ToJson
import org.joda.time.Instant

import scala.concurrent.Future
import scala.util.Try
/**
  * Represents an example of how to use the EventLogging SDK
  */
object SDKExample extends EventLogging with LazyLogging {

  case class Hello(name: String)

  def main(args: Array[String]): Unit = {

    val loop = args.headOption.flatMap(x => Try(x.toInt).toOption).getOrElse(10000000)

    val startTime = new Instant()
    logger.info("Current time: " + startTime)
    logger.info("Sending data ... ")

    val pingEvery = loop / 2
    var current = 1

    val sent = (1 to loop).map { _ =>

      if (pingEvery == current) {
        current = 1
        logger.info("Sending {} more records", pingEvery)
      }

      current = current + 1

      //One Event From Case Class
      val log0 = log(Hello("Que más"))

      val rLog0 = log0.commitAsync

      //From JValue
      val log1 = log(ToJson(Hello("Hola")).get, "My Category")

      val log2 = log(ToJson(Hello("Como estas")).get, "My another Category")

      //Let's unite them in order first in first out
      val log1_2 = log1 +> log2

      //Let's actually commit it
      val rLog1_2 = log1_2.commitAsync

      //Another Log From A Case Class
      val log3 = log(Hello("Hola"), "Category")

      val log4 = log(Hello("Como estas"))

      //Let's unite them in order first in last out
      val log3_4 = log3 <+ log4

      //Let's actually commit it
      val rLog3_4 = log3_4.commitAsync

      //Wanna have list of events and fold it

      val foldedLogs = List(
        log(Hello("Hello")),
        log(Hello("Hallo")),
        log(Hello("Hola"))
      )

      val rFoldedLogs = foldedLogs.commitAsync

      //By default the service class is the class extending or mixing the EventLogging trait
      //But you can also change it

      val log5 = log(Hello("Buenos Dias"), "THIS_IS_MY_CUSTOMIZED_SERVICE_CLASS", "Category")

      val rLog5 = log5.commitAsync

      //There are three types of commits supported
      // .commit
      // .commitAsync
      // .commitStealthAsync
      // Every one of these kinds of commits have different advantages.
      // With .commit we wait on the response from the producer.
      // With .commitAsync we create a Future that is returned instead.
      // With .commitStealthAsync we commit asynchronously but we don't care about the response.
      // It is like fire a forget.

      val log6 = log(Hello("Hallo"))
      val rLog6 = log6.commitAsync

      val log7 = log(Hello("Hi"))
      val rLog7 = Future.successful(log7.commitStealthAsync)

      val results = Vector(rLog0, rLog5, rLog6, rLog7) ++ rLog1_2 ++ rLog3_4 ++ rFoldedLogs

      results

    }.toVector.flatten

    val countDown = new CountDownLatch(1)

    Future.sequence(sent)
      .map(_ => countDown.countDown())
      .recover {
        case e: Exception =>
          countDown.countDown()
          logger.error("Something happened: {}", e.getMessage)
      }

    logger.info("Data Sent, waiting on acknowledgement")

    countDown.await()

    val finishTime = new Instant()
    val millis = startTime.millisBetween(finishTime)
    logger.info("Finish date: " + finishTime)
    logger.info("Total Records Sent:" + sent.size)
    logger.info("Rate: " + BigDecimal(sent.size) / BigDecimal(millis) + " records/millis")
    logger.info("Time Consumed:" + millis + " millis")

  }

}

/**
  * Represents an example of how to use the EventLogging SDK
  * It sends only one type of message.
  */
object SDKExample2 extends EventLogging with LazyLogging {

  case class Hello(name: String)

  def main(args: Array[String]): Unit = {

    val loop = args.headOption.flatMap(x => Try(x.toInt).toOption).getOrElse(500)

    val startTime = new Instant()
    logger.info("Current time: " + startTime)
    logger.info("Sending data ... ")

    val sent = (1 to loop).map { _ =>
      val log0 = log(Hello("Que más"))
      log0.commitAsync
    }

    val countDown = new CountDownLatch(1)

    Future.sequence(sent)
      .map(_ => countDown.countDown())
      .recover {
        case e: Exception =>
          countDown.countDown()
          logger.error("Something happened: {}", e.getMessage)
      }

    logger.info("Data Sent, waiting on acknowledgement")

    countDown.await()

    val finishTime = new Instant()
    val millis = startTime.millisBetween(finishTime)
    logger.info("Finish date: " + finishTime)
    logger.info("Total Records Sent:" + loop)
    logger.info("Rate: " + BigDecimal(loop) / BigDecimal(millis) + " records/millis")
    logger.info("Time Consumed:" + millis + " millis")

  }

}

