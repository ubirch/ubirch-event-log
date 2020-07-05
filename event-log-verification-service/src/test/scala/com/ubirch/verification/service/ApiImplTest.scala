package com.ubirch.verification.service

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ Matchers, WordSpec }
import skinny.http.HTTP

class ApiImplTest extends WordSpec with Matchers with ScalaFutures {

  HTTP.get("http://example.com/api?foo=bar")
}
