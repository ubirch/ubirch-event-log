package com.ubirch.verification.services

import java.security.PublicKey

import com.typesafe.config.Config
import com.ubirch.crypto.utils.Curve
import com.ubirch.crypto.{ GeneratorKeyFactory, PubKey }
import javax.inject._

trait TokenPublicKey {
  val pubKey: PubKey
  def publicKey: PublicKey = pubKey.getPublicKey
}

@Singleton
class DefaultTokenPublicKey @Inject() (config: Config) extends TokenPublicKey {

  private val publicKeyAsHex = config.getString("verification.tokenPublicKey")

  val pubKey: PubKey = GeneratorKeyFactory.getPubKey(publicKeyAsHex, Curve.PRIME256V1)

}
