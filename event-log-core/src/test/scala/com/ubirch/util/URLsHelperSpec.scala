package com.ubirch.util

import java.net.InetSocketAddress

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.TestBase
import com.ubirch.util.Exceptions.{ InvalidContactPointsException, NoContactPointsException }
import org.scalatest.mockito.MockitoSugar

class URLsHelperSpec extends TestBase with MockitoSugar with LazyLogging {

  "inetSocketAddressesString" must {

    "parse correctly single host" in {
      val urls = URLsHelper.inetSocketAddressesString("127.0.0.1:9042")
      assert(urls.size == 1)
      assert(urls.head == new InetSocketAddress("127.0.0.1", 9042))
    }

    "parse correctly multiple hosts" in {
      val urls = URLsHelper.inetSocketAddressesString("127.0.0.1:9042, 127.0.0.1:9042")
      assert(urls.size == 2)
      assert(urls == List(new InetSocketAddress("127.0.0.1", 9042), new InetSocketAddress("127.0.0.1", 9042)))
    }

    "throw InvalidContactPointsException when invalid port" in {
      def urls = URLsHelper.inetSocketAddressesString("127.0.0.1:port, 127.0.0.1:9042")
      assertThrows[InvalidContactPointsException](urls)
    }

    "throw InvalidContactPointsException when invalid token" in {
      def urls = URLsHelper.inetSocketAddressesString("127.0.0.1")
      assertThrows[InvalidContactPointsException](urls)
    }

    "throw InvalidContactPointsException when empty string" in {
      def urls = URLsHelper.inetSocketAddressesString("")
      assertThrows[NoContactPointsException](urls)
    }
  }

  "passThruWithCheck" must {
    "pass through OK" in {
      val urls = URLsHelper.passThruWithCheck("127.0.0.1:9042, 127.0.0.1:9042")
      assert(urls == "127.0.0.1:9042, 127.0.0.1:9042")
    }

    "throw InvalidContactPointsException when invalid port" in {
      def urls = URLsHelper.passThruWithCheck("127.0.0.1:port, 127.0.0.1:9042")
      assertThrows[InvalidContactPointsException](urls)
    }

    "throw InvalidContactPointsException when invalid token" in {
      def urls = URLsHelper.passThruWithCheck("127.0.0.1")
      assertThrows[InvalidContactPointsException](urls)
    }

    "throw InvalidContactPointsException when empty string" in {
      def urls = URLsHelper.passThruWithCheck("")
      assertThrows[NoContactPointsException](urls)
    }

  }

}
