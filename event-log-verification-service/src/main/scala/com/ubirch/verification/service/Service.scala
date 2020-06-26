package com.ubirch.verification.service

import com.ubirch.util.Boot
import com.ubirch.verification.service.util.udash.JettyServer

object Service extends Boot(LookupServiceBinder.modules) {

  def main(args: Array[String]): Unit = {

    get[JettyServer].startAndJoin()

  }

}
