package com.ubirch.verification.services

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import com.ubirch.verification.util.Exceptions.{ InvalidAllClaims, InvalidOtherClaims, InvalidSpecificClaim }
import com.ubirch.verification.util.LookupJsonSupport
import javax.inject.{ Inject, Singleton }
import pdi.jwt.{ Jwt, JwtAlgorithm }

import scala.util.Try

case class OtherClaims(role: Symbol, env: Symbol)

trait TokenVerification {
  def decodeAndVerify(jwt: String): Option[(Map[String, String], OtherClaims)]
}

@Singleton
class DefaultTokenVerification @Inject() (config: Config, tokenPublicKey: TokenPublicKey) extends TokenVerification with LazyLogging {

  final val ISSUER = "iss"
  final val SUBJECT = "sub"
  final val AUDIENCE = "aud"
  final val EXPIRATION = "exp"
  final val NOT_BEFORE = "nbf"
  final val ISSUED_AT = "iat"
  final val JWT_ID = "jti"

  private val validEnv = Symbol(config.getString("verification.jwt.env"))
  private val validIssuer = config.getString("verification.jwt.issuer")
  private val validRoles = config.getString("verification.jwt.roles").split(",").toSet.map(x => Symbol(x))

  def decodeAndVerify(jwt: String): Option[(Map[String, String], OtherClaims)] = {
    (for {
      (_, p, _) <- Jwt.decodeRawAll(jwt, tokenPublicKey.publicKey, Seq(JwtAlgorithm.ES256))
      otherClaims <- Try(LookupJsonSupport.FromString[OtherClaims](p).get)
        .recover { case e: Exception => throw InvalidOtherClaims(e.getMessage, jwt) }

      all <- Try(LookupJsonSupport.FromString[Map[String, String]](p).get)
        .recover { case e: Exception => throw InvalidAllClaims(e.getMessage, jwt) }

      _ <- all.get(ISSUER).toRight(InvalidSpecificClaim("Invalid issuer", p)).toTry.map(_ == validIssuer)
      _ <- all.get(SUBJECT).toRight(InvalidSpecificClaim("Invalid subject", p)).toTry.filter(_.nonEmpty)
      _ <- Try(otherClaims.role).filter(validRoles.contains)
        .recover { case e: Exception => throw InvalidSpecificClaim(e.getMessage, p) }
      _ <- Try(otherClaims.env).filter(_ == validEnv)
        .recover { case e: Exception => throw InvalidSpecificClaim(e.getMessage, p) }

    } yield {
      Some((all, otherClaims))
    }).recover {
      case e: InvalidSpecificClaim =>
        logger.error(s"invalid_token_specific_claim=${e.getMessage}", e)
        None
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
