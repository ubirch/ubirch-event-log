package com.ubirch.verification.models

import java.security.PublicKey

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.verification.util.Exceptions.{ InvalidAllClaims, InvalidOtherClaims }
import com.ubirch.verification.util.LookupJsonSupport
import pdi.jwt.{ Jwt, JwtAlgorithm }

import scala.util.Try

case class OtherClaims(role: Symbol)
object OtherClaims {

  def validate(otherClaims: OtherClaims): Option[OtherClaims] = {
    if (otherClaims.role == 'verifier) {
      Some(otherClaims)
    } else {
      None
    }
  }

}

object TokenVerification extends LazyLogging {

  def decodeAndVerify(jwt: String, publicKey: PublicKey): Option[(Map[String, String], OtherClaims)] = {
    (for {
      (_, p, _) <- Jwt.decodeRawAll(jwt, publicKey, Seq(JwtAlgorithm.ES256))
      otherClaims <- Try(LookupJsonSupport.FromString[OtherClaims](p).get)
        .recover { case e: Exception => throw InvalidOtherClaims(e.getMessage, jwt) }

      all <- Try(LookupJsonSupport.FromString[Map[String, String]](p).get)
        .recover { case e: Exception => throw InvalidAllClaims(e.getMessage, jwt) }

    } yield {
      OtherClaims.validate(otherClaims).map((all, _))
    }).recover {
      case e: InvalidAllClaims =>
        logger.error(s"invalid_token_all_claims=${e.getMessage}", e)
        None
      case e: InvalidOtherClaims =>
        logger.error(s"invalid_token_other_claims=${e.getMessage}", e)
        None
      case e: Exception =>
        logger.error(s"invalid_token=${e.getMessage}", e)
        None
    }.getOrElse(None)

  }

}
