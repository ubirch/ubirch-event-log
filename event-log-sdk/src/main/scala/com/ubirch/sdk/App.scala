package com.ubirch.sdk

import com.ubirch.util.ToJson

object App extends EventLogging {

  def main(args: Array[String]): Unit = {

    case class Hello(name: String)

    //From JSValue
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


  }

}

