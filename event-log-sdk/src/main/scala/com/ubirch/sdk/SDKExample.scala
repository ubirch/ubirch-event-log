package com.ubirch.sdk

import com.ubirch.util.ToJson

/**
  * Represents an example of how to use the EventLogging SDK
  */
object SDKExample extends EventLogging {

  case class Hello(name: String)

  def main(args: Array[String]): Unit = {

    //One Event From Case Class
    val log0 = log(Hello("Que más"))

    log0.commit

    //From JValue
    val log1 = log(ToJson(Hello("Hola")).get, "My Category")

    val log2 = log(ToJson(Hello("Como estas")).get, "My another Category")

    //Let's unite them in order first in first out
    val log1_2 = log1 +> log2

    //Let's actually commit it
    log1_2.commit

    //Another Log From A Case Class
    val log3 = log(Hello("Hola"), "Category")

    val log4 = log(Hello("Como estas"))

    //Let's unite them in order first in last out
    val log3_4 = log3 <+ log4

    //Let's actually commit it
    log3_4.commit

    //Wanna have list of events and fold it

    val foldedLogs = List(
      log(Hello("Hello")),
      log(Hello("Hallo")),
      log(Hello("Hola"))
    )

    foldedLogs.commit

    //By default the service class is the class extending or mixing the EventLogging trait
    //But you can also change it

    val log5 = log(Hello("Buenos Dias"), "THIS_IS_MY_CUSTOMIZED_SERVICE_CLASS", "Category")

    log5.commit

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
    log6.commitAsync

    val log7 = log(Hello("Hi"))
    log7.commitStealthAsync

  }

}

