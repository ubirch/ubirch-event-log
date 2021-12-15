package com.ubirch.verification.controllers

import com.ubirch.verification.controllers.Api.Response
import com.ubirch.verification.models.{ AnchorsNoPath, BlockchainInfo, Normal, ResponseForm, ShortestPath, Simple, UpperLower }

import scala.concurrent.Future

trait Versions {
  _: DefaultApi =>

  val controllerHelpers: ControllerHelpers

  import controllerHelpers._

  def healthCheck: Future[String] = {
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

    def verifyUPP(hash: Array[Byte]): Future[Response] = {
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

    def getUPP(hash: Array[Byte], disableRedisLookup: Boolean, authToken: String, origin: String): Future[Response] = {
      registerMetrics("v2.upp") {
        authorization(authToken) { claims =>
          validateClaimsAndRegisterAcctEvent(origin, claims) {
            lookupBase(hash, Simple, AnchorsNoPath, Normal, disableRedisLookup)
          }
        }
      }
    }

    def verifyUPP(hash: Array[Byte], authToken: String, origin: String): Future[Response] = {
      registerMetrics("v2.simple") {
        authorization(authToken) { claims =>
          validateClaimsAndRegisterAcctEvent(origin, claims) {
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

    def verifyUPPWithUpperBound(hash: Array[Byte], responseForm: String, blockchainInfo: String, authToken: String, origin: String): Future[Response] = {
      registerMetrics("v2.anchor") {
        authorization(authToken) { claims =>
          validateClaimsAndRegisterAcctEvent(origin, claims) {
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

    def verifyUPPWithUpperAndLowerBound(hash: Array[Byte], responseForm: String, blockchainInfo: String, authToken: String, origin: String): Future[Response] = {
      registerMetrics("v2.record") {
        authorization(authToken) { claims =>
          validateClaimsAndRegisterAcctEvent(origin, claims) {
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
