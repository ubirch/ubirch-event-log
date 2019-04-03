package com.ubirch.util

import com.typesafe.config.Config
import com.ubirch.ConfPaths.CryptoConfPaths
import com.ubirch.crypto.utils.{ Algorithms, Hashs }
import com.ubirch.crypto.{ GeneratorKeyFactory, PrivKey }

object SigningHelper extends CryptoConfPaths {

  def signData(pk: PrivKey, payload: Array[Byte]): Array[Byte] = {
    pk.sign(payload, Hashs.SHA512)
  }

  def signData(pkString: String, payload: Array[Byte]): Array[Byte] = {
    val pk: PrivKey = GeneratorKeyFactory.getPrivKey(pkString.take(64), Algorithms.EDDSA)
    signData(pk, payload)
  }

  def signData(config: Config, payload: Array[Byte]): Array[Byte] = {
    val pkString = config.getString(SERVICE_PK)
    signData(pkString, payload)
  }

}
