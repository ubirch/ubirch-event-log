package com.ubirch.chainer.models

import com.ubirch.TestBase
import com.ubirch.chainer.models.Hash.{ BytesData, HexStringData, StringData }

import org.bouncycastle.util.encoders.Hex

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class HashSpec extends TestBase {

  def getHash(data: Array[Byte]): Array[Byte] = {
    val messageDigest = MessageDigest.getInstance("SHA-512")
    messageDigest.update(data)
    messageDigest.digest()
  }

  "A Hash" must {

    "apply(v: HashData[T])" in {
      val hashHola = getHash("hola".getBytes(StandardCharsets.UTF_8))
      assert(Hash(StringData("hola", StandardCharsets.UTF_8)).rawValue sameElements hashHola)
      assert(Hash(BytesData("hola".getBytes(StandardCharsets.UTF_8))).rawValue sameElements hashHola)
      assert(Hash(HexStringData(Hex.toHexString("hola".getBytes(StandardCharsets.UTF_8)))).rawValue sameElements hashHola)
    }

    "apply(v1: BytesData, v2: BytesData)" in {

      val hashHolaAdios = getHash(
        Array.concat(
          "hola".getBytes(StandardCharsets.UTF_8),
          "adios".getBytes(StandardCharsets.UTF_8)
        )
      )

      assert(
        Hash(
          BytesData("hola".getBytes(StandardCharsets.UTF_8)),
          BytesData("adios".getBytes(StandardCharsets.UTF_8))
        ).rawValue sameElements hashHolaAdios
      )
    }

    "apply(v1: HexStringData, v2: HexStringData)" in {

      val hashHolaAdios = getHash(
        Array.concat(
          "hola".getBytes(StandardCharsets.UTF_8),
          "adios".getBytes(StandardCharsets.UTF_8)
        )
      )

      assert(
        Hash(
          HexStringData(Hex.toHexString("hola".getBytes(StandardCharsets.UTF_8))),
          HexStringData(Hex.toHexString("adios".getBytes(StandardCharsets.UTF_8)))
        ).rawValue sameElements hashHolaAdios
      )
    }

    "apply(v1: StringData, v2: StringData)" in {

      val hashHolaAdios = getHash(
        Array.concat(
          "hola".getBytes(StandardCharsets.UTF_8),
          "adios".getBytes(StandardCharsets.UTF_8)
        )
      )

      assert(Hash(StringData("hola"), StringData("adios")).rawValue sameElements hashHolaAdios)
    }

  }
}
