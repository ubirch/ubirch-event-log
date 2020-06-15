package com.ubirch.util

import java.nio.charset.StandardCharsets

import com.typesafe.config.Config
import com.ubirch.ConfPaths.CryptoConfPaths
import com.ubirch.crypto.utils.Curve
import com.ubirch.crypto.{GeneratorKeyFactory, PrivKey}
import org.apache.commons.codec.binary.Hex

/**
  * A signing convenience for the EventLog types
  */
object SigningHelper extends CryptoConfPaths {

  def getBytesFromString(string: String): Array[Byte] = {
    string.getBytes(StandardCharsets.UTF_8)
  }

  def bytesToHex(bytes: Array[Byte]): String = {
    Hex.encodeHexString(bytes)
  }

  def signAndGetAsHex(config: Config, payload: Array[Byte]): String = {
    bytesToHex(signData(config, payload))
  }

  def signAndGetAsHex(pkString: String, payload: Array[Byte]): String = {
    bytesToHex(signData(pkString, payload))
  }

  def signData(pk: PrivKey, payload: Array[Byte]): Array[Byte] = {
    pk.sign(payload)
  }

  def signData(pkString: String, payload: Array[Byte]): Array[Byte] = {
    val pk = GeneratorKeyFactory.getPrivKey(pkString.take(64), Curve.Ed25519)
    signData(pk, payload)
  }

  def signData(config: Config, payload: Array[Byte]): Array[Byte] = {
    val pkString = config.getString(SERVICE_PK)
    signData(pkString, payload)
  }

}
