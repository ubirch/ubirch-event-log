package com.ubirch

import com.ubirch.service.ExtServiceBinder
import com.ubirch.service.rest.RestService
import com.ubirch.services.ServiceBinder
import com.ubirch.util.Boot

import scala.language.postfixOps

object Service extends Boot(ServiceBinder.modules ++ ExtServiceBinder.modules) {

  def main(args: Array[String]): Unit = {

    logger.info("Starting Rest")

    val restEndpoint = get[RestService]
    restEndpoint.start

  }

}
