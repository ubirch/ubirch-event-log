package com.ubirch.verification

import com.ubirch.util.Boot
import com.ubirch.verification.util.udash.JettyServer

object Service extends Boot(LookupServiceBinder.modules) {

  def main(args: Array[String]): Unit = {

    get[JettyServer].startAndJoin()

  }

}
