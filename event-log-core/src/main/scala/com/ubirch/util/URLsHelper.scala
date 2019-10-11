package com.ubirch.util

import java.net.{ InetSocketAddress, URL }

import com.ubirch.util.Exceptions.{ InvalidContactPointsException, NoContactPointsException }

import scala.util.Try

/**
  * A helper convenience for URL related work
  */
object URLsHelper {

  /**
    * Parses a string to InetSocketAddress
    * @param endpoints represents the input string
    *                      A correct string would look like: 127.0.0.1:9042, 127.0.0.2:9042
    * @return InetSocketAddress
    */
  def inetSocketAddressesString(endpoints: String): List[InetSocketAddress] = {
    try {
      if (endpoints.nonEmpty) {
        endpoints
          .split(",")
          .toList
          .map(_.trim)
          .filter(_.nonEmpty)
          .map { entry =>
            entry.split(":").toList.map(_.trim).filter(_.nonEmpty) match {
              case List(ip, portNumber) => new InetSocketAddress(ip, portNumber.toInt)
              case _ => throw InvalidContactPointsException(s"The string provided is malformed: $endpoints")
            }
          }
      } else {
        throw NoContactPointsException("No endpoints provided.")
      }
    } catch {
      case e: NumberFormatException =>
        throw InvalidContactPointsException(s"The string provided is malformed: $endpoints : ${e.getMessage}")
      case e: NoContactPointsException =>
        throw e
      case e: Exception =>
        throw InvalidContactPointsException(e.getMessage)
    }
  }

  def passThruWithCheck(endpoints: String): String = {
    try {
      inetSocketAddressesString(endpoints)
      endpoints
    } catch {
      case e: Exception =>
        throw e
    }

  }

  def toURL(endpoint: String): Try[URL] = Try(new URL(endpoint))

}
