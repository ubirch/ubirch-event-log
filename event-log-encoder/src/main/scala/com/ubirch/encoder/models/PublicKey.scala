package com.ubirch.encoder.models

/**
  * Represents a public key
  * @param hardwareId hardware id that created the key
  * @param publicKey public key
  */
case class PublicKey(hardwareId: String, publicKey: String)
