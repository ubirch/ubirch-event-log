package com.ubirch.encoder.models

/**
  * Represents a public key
  * @param id of the entity that created the key
  * @param publicKey public key
  */
case class PublicKey(id: String, publicKey: String)
