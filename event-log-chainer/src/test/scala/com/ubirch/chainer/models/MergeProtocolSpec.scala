package com.ubirch.chainer.models

import com.ubirch.TestBase

import org.bouncycastle.util.encoders.Hex

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class MergeProtocolSpec extends TestBase {

  def getHash(data: Array[Byte]): Array[Byte] = {
    val messageDigest = MessageDigest.getInstance("SHA-512")
    messageDigest.update(data)
    messageDigest.digest()
  }

  "A MergeProtocol.V2_HexString" must {

    "have expected values" in {
      assert(MergeProtocol.V2_HexString.version == ((2 << 4) | 0x01))

      val hashHolaAdios = getHash(
        Array.concat(
          "hola".getBytes(StandardCharsets.UTF_8),
          "adios".getBytes(StandardCharsets.UTF_8)
        )
      )

      MergeProtocol.V2_HexString.equals("a", "a")

      assert(MergeProtocol.V2_HexString.merger(
        Hex.toHexString("hola".getBytes(StandardCharsets.UTF_8)),
        Hex.toHexString("adios".getBytes(StandardCharsets.UTF_8))
      ) == Hex.toHexString(hashHolaAdios))

    }

  }

  "A MergeProtocol.V2_Bytes" must {

    "have expected values" in {
      assert(MergeProtocol.V2_Bytes.version == ((2 << 4) | 0x02))

      val hashHolaAdios = getHash(
        Array.concat(
          "hola".getBytes(StandardCharsets.UTF_8),
          "adios".getBytes(StandardCharsets.UTF_8)
        )
      )

      MergeProtocol.V2_Bytes.equals("a".getBytes(StandardCharsets.UTF_8), "a".getBytes(StandardCharsets.UTF_8))

      assert(MergeProtocol.V2_Bytes.merger(
        "hola".getBytes(StandardCharsets.UTF_8),
        "adios".getBytes(StandardCharsets.UTF_8)
      ) sameElements hashHolaAdios)

    }

  }

}
