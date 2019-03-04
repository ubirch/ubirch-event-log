package com.ubirch.models

import com.ubirch.TestBase
import com.ubirch.util.Hasher

import scala.language.implicitConversions

case class SomeDataTypeFromKafka(id: String, data: String)

object SomeDataTypeFromKafka {
  implicit def chainable(t: SomeDataTypeFromKafka) = Chainable(t.id, t)
}

class ChainerSpec extends TestBase {

  "A Chainer" must {

    "group based on the chainable id" in {

      val listOfData = List(
        SomeDataTypeFromKafka("vegetables", "eggplant"),
        SomeDataTypeFromKafka("vegetables", "artichoke"),
        SomeDataTypeFromKafka("fruits", "banana")
      )

      val nodes = Chainer(listOfData).createGroups.getGroups

      val expected = List(
        List(
          SomeDataTypeFromKafka("fruits", "banana")
        ),
        List(
          SomeDataTypeFromKafka("vegetables", "eggplant"),
          SomeDataTypeFromKafka("vegetables", "artichoke")
        )
      )

      assert(nodes.nonEmpty)
      assert(nodes.size == 2)
      assert(nodes.size == 2)

      assert(nodes.exists(_.size == 1))
      assert(nodes.find(_.size == 1) == Option(List(
        SomeDataTypeFromKafka("fruits", "banana")
      )))

      assert(nodes.exists(_.size == 2))
      assert(nodes.find(_.size == 2) == Option(List(
        SomeDataTypeFromKafka("vegetables", "eggplant"),
        SomeDataTypeFromKafka("vegetables", "artichoke")
      )))

    }

    "group empty" in {

      val listOfData: List[SomeDataTypeFromKafka] = List()

      val nodes = Chainer(listOfData).createGroups.getGroups

      assert(nodes.isEmpty)

    }

    "createSeedHashes based on the chainable id" in {

      val listOfData = List(
        SomeDataTypeFromKafka("vegetables", "eggplant"),
        SomeDataTypeFromKafka("vegetables", "artichoke"),
        SomeDataTypeFromKafka("fruits", "banana")
      )

      val nodes = Chainer(listOfData).createGroups.createSeedHashes.getHashes

      val expected = List(
        List(
          "592ecd7f1fb08364c3382ffe2f137cec33e37c49dbe45f593b67be11d0ea6d94aa4e092397c220bb702052c378e44710b13f81014b75e8c9080c2acd65ea8fa8"
        ),
        List(
          "de7d06eb8fa30a130984f17eef9285380540ab9d9d4a10c8f56d411406ad1fcfe7766f2af0071a7eae9c62b2363b25766f3245931dbc2320c365cea1cf007adc",
          "e03d11c7975a32f5329c95763085cb0ba4c47a2f30de288de2e52af82f048b68246458b7b29648d1799312fe335588951bde7e67ecab7692080a1a296f473bab"
        )
      )

      assert(nodes.nonEmpty)
      assert(nodes.size == 2)
      assert(nodes.size == 2)
      assert(nodes.exists(_.size == 1))
      assert(nodes.exists(_.size == 2))

      assert(nodes == expected)

      val banana = Hasher.hash(SomeDataTypeFromKafka("fruits", "banana").toString)
      val eggplant = Hasher.hash(SomeDataTypeFromKafka("vegetables", "eggplant").toString)
      val artichoke = Hasher.hash(SomeDataTypeFromKafka("vegetables", "artichoke").toString)

      assert(nodes.contains(List(banana)))
      assert(nodes.contains(List(eggplant, artichoke)))

    }

  }
}
