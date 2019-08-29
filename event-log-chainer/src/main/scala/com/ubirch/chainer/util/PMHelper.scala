package com.ubirch.chainer.util

import com.ubirch.protocol.ProtocolMessage
import com.ubirch.util.UUIDHelper

import scala.util.Random

object PMHelper {
  def createPM = {
    val pmId = Random.nextInt()
    val pm = new ProtocolMessage(1, UUIDHelper.randomUUID, 0, pmId)
    pm.setSignature(org.bouncycastle.util.Strings.toByteArray("1111"))
    pm.setChain(org.bouncycastle.util.Strings.toByteArray("2222"))
    pm
  }
}
