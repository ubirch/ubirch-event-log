package com.ubirch.sdk

import com.ubirch.util.ToJson

object App extends EventLogging {

  def main(args: Array[String]): Unit = {

    case class Hello(name: String)

    //From JSValue

    def log1 = log(ToJson(Hello("Hola")).get, "My Category")

    log1.commit

    val log2 = log(Hello("Hello"), "My Category")

    val log1_2 = log1 -> log2

    log1_2.commit

  }

}

