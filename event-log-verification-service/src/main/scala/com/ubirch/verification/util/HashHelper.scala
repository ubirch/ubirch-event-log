package com.ubirch.verification.util

import java.nio.charset.StandardCharsets
import java.util.Base64

object HashHelper {

  def b64(x: Array[Byte]): String = if (x != null) Base64.getEncoder.encodeToString(x) else null

  def bytesToPrintableId(x: Array[Byte]): String =
    if (x.exists(c => !(c.toChar.isLetterOrDigit || c == '=' || c == '/' || c == '+'))) b64(x)
    else new String(x, StandardCharsets.UTF_8)

}
