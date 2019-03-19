package com.ubirch.util

import com.google.inject.Inject
import com.typesafe.config.Config
import com.ubirch.ConfPaths
import com.ubirch.crypto.utils.{Algorithms, Hashs}
import com.ubirch.crypto.{GeneratorKeyFactory, PrivKey}
import javax.inject.Singleton

@Singleton
class SigningHelper @Inject()(config: Config) {

  private final val pk = config.getString(ConfPaths.Crypto.SERVICE_PK)

  def signData(payload: Array[Byte]): Array[Byte] = {
    val pkString = config.getString(ConfPaths.Crypto.SERVICE_PK)
    val pk: PrivKey = GeneratorKeyFactory.getPrivKey(pkString.take(64), Algorithms.EDDSA)
    pk.sign(payload, Hashs.SHA512)
  }

}
