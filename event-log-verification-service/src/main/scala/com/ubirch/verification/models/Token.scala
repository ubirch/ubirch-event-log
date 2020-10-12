package com.ubirch.verification.models

import java.security.PublicKey

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.verification.util.Exceptions.InvalidOtherClaims
import com.ubirch.verification.util.LookupJsonSupport
import pdi.jwt.{ Jwt, JwtAlgorithm }

import scala.util.Try

case class OtherClaims(role: Symbol)
object OtherClaims {

  def validate(otherClaims: OtherClaims): Boolean = otherClaims.role == 'verifier

}

object TokenVerification extends LazyLogging {

  def decodeAndVerify(jwt: String, publicKey: PublicKey): Boolean = {
    (for {
      (_, p, _) <- Jwt.decodeRawAll(jwt, publicKey, Seq(JwtAlgorithm.ES256))
      _ = println(p)
      otherClaims <- Try(LookupJsonSupport.FromString[OtherClaims](p).get)
        .recover { case e: Exception => throw InvalidOtherClaims(e.getMessage, jwt) }

    } yield {
      OtherClaims.validate(otherClaims)
    }).recover {
      case e: InvalidOtherClaims =>
        logger.error(s"invalid_token_other_claims=${e.getMessage}", e)
        false
      case e: Exception =>
        logger.error(s"invalid_token=${e.getMessage}", e)
        false
    }.getOrElse(false)

  }

}
