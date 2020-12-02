package com.ubirch.verification.controllers

import com.ubirch.verification.controllers.Api.Response
import com.ubirch.verification.models.{ AnchorsNoPath, BlockchainInfo, Normal, ResponseForm, ShortestPath, Simple, UpperLower }

import scala.concurrent.{ ExecutionContext, Future }

trait Versions {
  _: DefaultApi =>

  val controllerHelpers: ControllerHelpers

  import controllerHelpers._

  def health: Future[String] = {
    registerMetrics("health") { () =>
      Future.successful("ok")
    }
  }

  class Api() {

    def getUPP(hash: Array[Byte], disableRedisLookup: Boolean): Future[Response] = {
      registerMetrics("upp") { () =>
        lookupBase(hash, Simple, AnchorsNoPath, Normal, disableRedisLookup).map(_.response)
      }
    }

    def verifyUPP(hash: Array[Byte])(implicit ec: ExecutionContext): Future[Response] = {
      registerMetrics("simple") { () =>
        verifyBase(
          hash,
          Simple,
          AnchorsNoPath,
          Normal
        ).map(_.response)
      }
    }

    def verifyUPPWithUpperBound(hash: Array[Byte], responseForm: String, blockchainInfo: String): Future[Response] = {
      registerMetrics("anchor") { () =>
        verifyBase(
          hash,
          ShortestPath,
          ResponseForm.fromString(responseForm).getOrElse(AnchorsNoPath),
          BlockchainInfo.fromString(blockchainInfo).getOrElse(Normal)
        ).map(_.response)
      }
    }

    def verifyUPPWithUpperAndLowerBound(hash: Array[Byte], responseForm: String, blockchainInfo: String): Future[Response] = {
      registerMetrics("record") { () =>
        verifyBase(
          hash,
          UpperLower,
          ResponseForm.fromString(responseForm).getOrElse(AnchorsNoPath),
          BlockchainInfo.fromString(blockchainInfo).getOrElse(Normal)
        ).map(_.response)
      }
    }

  }

  class Api2() {

    def getUPP(hash: Array[Byte], disableRedisLookup: Boolean, authToken: String): Future[Response] = {
      registerMetrics("v2.upp") {
        authorization(authToken) { accessInfo =>
          registerAcctEvent(accessInfo) {
            lookupBase(hash, Simple, AnchorsNoPath, Normal, disableRedisLookup)
          }
        }
      }
    }

    def verifyUPP(hash: Array[Byte], authToken: String): Future[Response] = {
      registerMetrics("v2.simple") {
        authorization(authToken) { accessInfo =>
          registerAcctEvent(accessInfo) {
            verifyBase(
              hash,
              Simple,
              AnchorsNoPath,
              Normal
            )
          }
        }
      }
    }

    def verifyUPPWithUpperBound(hash: Array[Byte], responseForm: String, blockchainInfo: String, authToken: String): Future[Response] = {
      registerMetrics("v2.anchor") {
        authorization(authToken) { accessInfo =>
          registerAcctEvent(accessInfo) {
            verifyBase(
              hash,
              ShortestPath,
              ResponseForm.fromString(responseForm).getOrElse(AnchorsNoPath),
              BlockchainInfo.fromString(blockchainInfo).getOrElse(Normal)
            )
          }
        }
      }
    }

    def verifyUPPWithUpperAndLowerBound(hash: Array[Byte], responseForm: String, blockchainInfo: String, authToken: String): Future[Response] = {
      registerMetrics("v2.record") {
        authorization(authToken) { accessInfo =>
          registerAcctEvent(accessInfo) {
            verifyBase(
              hash,
              UpperLower,
              ResponseForm.fromString(responseForm).getOrElse(AnchorsNoPath),
              BlockchainInfo.fromString(blockchainInfo).getOrElse(Normal)
            )
          }
        }
      }
    }
  }

}
