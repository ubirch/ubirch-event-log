package com.ubirch.verification

import com.ubirch.util.Boot
import com.ubirch.verification.util.udash.JettyServer

import scala.util.{Failure, Success}

object Service extends Boot(LookupServiceBinder.modules) {

  def main(args: Array[String]): Unit = {

    getAsTry[JettyServer] match {
      case Failure(e) =>
        logger.error("Error at system initialization", e.getMessage, e)
        sys.exit(1)
      case Success(value) => value.startAndJoin()
    }

  }

}
