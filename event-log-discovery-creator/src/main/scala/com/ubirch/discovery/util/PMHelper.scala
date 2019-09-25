package com.ubirch.discovery.util

import com.ubirch.protocol.ProtocolMessage
import com.ubirch.util.UUIDHelper

import scala.util.Random

object PMHelper {
  def createPM = {
    val pmId = Random.nextInt()
    val pm = new ProtocolMessage(1, UUIDHelper.randomUUID, 0, pmId)
    pm.setSignature(org.bouncycastle.util.Strings.toByteArray(Random.nextString(10)))
    pm.setChain(org.bouncycastle.util.Strings.toByteArray(Random.nextString(10)))
    pm
  }

  def createPM2 = {
    val pmId = Random.nextInt()
    val pm = new ProtocolMessage(1, UUIDHelper.randomUUID, 0, pmId)
    pm.setSignature(org.bouncycastle.util.Strings.toByteArray(Random.nextString(10)))
    pm
  }

  def createPM3 = {
    val pmId = Random.nextInt()
    val pm = new ProtocolMessage(1, null, 0, pmId)
    pm.setSignature(org.bouncycastle.util.Strings.toByteArray(Random.nextString(10)))
    pm
  }
}
