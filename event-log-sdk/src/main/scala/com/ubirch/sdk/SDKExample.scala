package com.ubirch.sdk

import java.util.concurrent.{ CountDownLatch, TimeUnit }

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.util.Implicits.enrichedInstant
import com.ubirch.util.{ EventLogJsonSupport, UUIDHelper }
import org.joda.time.Instant

import scala.concurrent.Future
import scala.util.Try

/**
  * Represents an example of how to use the EventLogging SDK
  */
object SDKExample extends EventLogging with LazyLogging {

  case class Hello(name: String)

  def main(args: Array[String]): Unit = {

    val loop = args.headOption.flatMap(x => Try(x.toInt).toOption).getOrElse(1000000)

    val startTime = new Instant()
    logger.info("Current time: " + startTime)
    logger.info(s"Sending data $loop records ... ")

    val pingEvery = loop / 2
    var current = 1

    val sent = (1 to loop).map { _ =>

      if (pingEvery == current) {
        current = 1
        logger.info("{} records sent", pingEvery)
      }

      current = current + 1

      def customer = UUIDHelper.randomUUID.toString

      //One Event From Case Class
      val log0 = log(Hello("Que más"))
        .withCustomerId(customer)
        .withRandomNonce

      val rLog0 = log0.commitAsync

      //From JValue
      val log1 = log(EventLogJsonSupport.ToJson(Hello("Hola")).get, "My Category")
        .withCustomerId(customer)
        .withRandomNonce

      val log2 = log(EventLogJsonSupport.ToJson(Hello("Como estas")).get, "My another Category")
        .withCustomerId(customer)
        .withRandomNonce

      //Let's unite them in order first in first out
      val log1_2 = log1 +> log2

      //Let's actually commit it
      val rLog1_2 = log1_2.commitAsync

      //Another Log From A Case Class
      val log3 = log(Hello("Hola"), "Category")
        .withCustomerId(customer)
        .withRandomNonce

      val log4 = log(Hello("Como estas"))
        .withCustomerId(customer)
        .withRandomNonce

      //Let's unite them in order first in last out
      val log3_4 = log3 <+ log4

      //Let's actually commit it
      val rLog3_4 = log3_4.commitAsync

      //Wanna have list of events and fold it

      val foldedLogs = List(
        log(Hello("Hello")).withCustomerId(customer).withRandomNonce,
        log(Hello("Hallo")).withCustomerId(customer).withRandomNonce,
        log(Hello("Hola")).withCustomerId(customer).withRandomNonce
      )

      val rFoldedLogs = foldedLogs.commitAsync

      //By default the service class is the class extending or mixing the EventLogging trait
      //But you can also change it

      val log5 = log(Hello("Buenos Dias"), "THIS_IS_MY_CUSTOMIZED_SERVICE_CLASS", "Category")
        .withCustomerId(customer)
        .withRandomNonce

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
        .withCustomerId(customer)
        .withRandomNonce
      val rLog6 = log6.commitAsync

      val log7 = log(Hello("Hi"))
        .withCustomerId(customer)
        .withRandomNonce
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

  def main(args: Array[String]): Unit = {

    val loop = args.headOption.flatMap(x => Try(x.toInt).toOption).getOrElse(1000000)
    val parallel = false

    val startTime = new Instant()
    logger.info("Current time: " + startTime)
    logger.info(s"Sending data $loop records ... ")

    val countDown = new CountDownLatch(loop)

    def go = {
      log(
        """
          |{
          |   "ubirchPacket":{
          |      "hint":0,
          |      "payload":"c29tZSBieXRlcyEAAQIDnw==",
          |      "signature":"5aTelLQBerVT/vJiL2qjZCxWxqlfwT/BaID0zUVy7LyUC9nUdb02//aCiZ7xH1HglDqZ0Qqb7GyzF4jtBxfSBg==",
          |      "signed":"lRKwjni1ymWXEeiBhcg+pwAOTQCwc29tZSBieXRlcyEAAQIDnw==",
          |      "uuid":"8e78b5ca-6597-11e8-8185-c83ea7000e4d",
          |      "version":34
          |   },
          |   "context":{
          |      "hardwareInfo":{
          |         "model":"armv6l",
          |         "revision":"4.4.50-rt66+",
          |         "serialNumber":"8e78b5ca-6597-11e8-8185-c83ea7000e4d"
          |      },
          |      "customerId":"device_8e78b5ca-6597-11e8-8185-c83ea7000e4d",
          |      "deviceName":"8e78b5ca-6597-11e8-8185-c83ea7000e4d",
          |      "type":null,
          |      "creationTime":"2018-06-01T12:32:28Z",
          |      "lastUpdateTime":"2019-01-17T19:41:33Z",
          |      "cumulocityUrl":"https://ubirch.cumulocity.com/inventory/managedObjects/13651"
          |   }
          |}
        """.stripMargin,
        serviceClass = "SDKExample2",
        category = "testdata"
      ).withRandomNonce
        .commitAsync
        .map(_ => countDown.countDown())
        .recover {
          case e: Exception =>
            countDown.countDown()
            logger.error("Something happened: {}", e.getMessage)
        }
    }

    if (parallel) {
      Future {
        Iterator
          .continually(go)
          .take(loop / 2)
          .foreach(x => x)
      }

      Future {
        Iterator
          .continually(go)
          .take(loop / 2)
          .foreach(x => x)
      }
    } else {
      Iterator
        .continually(go)
        .take(loop)
        .foreach(x => x)
    }

    logger.info("Data Sent, waiting on acknowledgement")

    val timeout = 5
    val timeoutUnit = TimeUnit.MINUTES
    val countDownRes = countDown.await(timeout, timeoutUnit)

    if (countDownRes) {
      val finishTime = new Instant()
      val millis = startTime.millisBetween(finishTime)
      logger.info("Finish date: " + finishTime)
      logger.info("Total Records Sent:" + loop)
      logger.info("Rate: " + BigDecimal(loop) / BigDecimal(millis) + " records/millis")
      logger.info("Time Consumed:" + millis + " millis")
    } else {
      logger.error(s"Request timed out after {} $timeoutUnit", timeout)
    }

  }

}

/**
  * Represents an example of how to use the EventLogging SDK plugged in
  * directly to the EventLog Chainer
  * It sends only one type of message.
  */

object SDKExample3 extends EventLogging with LazyLogging {

  case class Hello(name: String)

  def main(args: Array[String]): Unit = {

    val loop = args.headOption.flatMap(x => Try(x.toInt).toOption).getOrElse(10)

    val startTime = new Instant()
    logger.info("Current time: " + startTime)
    logger.info(s"Sending data $loop records ... ")

    val customerIds = List("Sun", "Earth", "Marz")

    val sent = customerIds.flatMap { x =>
      (1 to loop).map { _ =>
        val log0 = log(Hello("Que más"))
          .withCustomerId(x)
          .withRandomNonce

        log0.commitAsync
      }
    }

    val sentSize = sent.size

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
    logger.info("Total Records Sent:" + sentSize)
    logger.info("Rate: " + BigDecimal(sentSize) / BigDecimal(millis) + " records/millis")
    logger.info("Time Consumed:" + millis + " millis")

    System.exit(0)
  }

}

