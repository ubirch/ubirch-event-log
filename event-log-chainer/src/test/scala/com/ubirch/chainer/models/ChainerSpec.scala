package com.ubirch.chainer.models

import com.ubirch.TestBase
import com.ubirch.chainer.models.Comparators.stringComparator
import com.ubirch.chainer.util.Hasher
import com.ubirch.util.EventLogJsonSupport

import scala.language.implicitConversions
import scala.util.Random

case class SomeDataTypeFromKafka(id: String, data: String)

object SomeDataTypeFromKafka {
  implicit def chainable(t: SomeDataTypeFromKafka): Chainable[SomeDataTypeFromKafka, String, String] = new Chainable[SomeDataTypeFromKafka, String, String](t) {
    override def hash: String = Hasher.hash(t.data)
    override def groupId: String = t.id
  }
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
          "f8e3183d38e6c51889582cb260ab825252f395b4ac8fb0e6b13e9a71f7c10a80d5301e4a949f2783cb0c20205f1d850f87045f4420ad2271c8fd5f0cd8944be3"
        ),
        List(
          "b11191a2a192bb0651a6c33e5a937923665f9c69b26f39ba2a4093717ce4ffc686d201140b81ebade1d88cefd2e1817726531f6b6327d817df2777f7aed1bd34",
          "960ad8aedb8e66f528980427823bb4d17c6c12d7126c30a0a601c00b88231d1fd8b1c5cee6651444266368f9d71af37451469ec4fa98d87cf20d95585eedf9e6"
        )
      )

      assert(nodes.nonEmpty)
      assert(nodes.size == 2)
      assert(nodes.size == 2)
      assert(nodes.exists(_.size == 1))
      assert(nodes.exists(_.size == 2))

      assert(nodes == expected)

      val banana = Hasher.hash(SomeDataTypeFromKafka("fruits", "banana").data)
      val eggplant = Hasher.hash(SomeDataTypeFromKafka("vegetables", "eggplant").data)
      val artichoke = Hasher.hash(SomeDataTypeFromKafka("vegetables", "artichoke").data)

      assert(nodes.contains(List(banana)))
      assert(nodes.contains(List(eggplant, artichoke)))

    }

    "create nodes of hashes" in {

      val listOfData = List(
        SomeDataTypeFromKafka("vegetables", "eggplant"),
        SomeDataTypeFromKafka("vegetables", "artichoke"),
        SomeDataTypeFromKafka("fruits", "banana"),
        SomeDataTypeFromKafka("fruits", "apple")
      )

      val nodes = Chainer(listOfData)
        .withMergerFunc(Hasher.mergeAndHash)
        .withBalancerFunc(_ => Chainer.getEmptyNodeVal)
        .createGroups
        .createSeedHashes
        .createSeedNodes(true)
        .getNodes

      val banana = Hasher.hash(SomeDataTypeFromKafka("fruits", "banana").data)
      val apple = Hasher.hash(SomeDataTypeFromKafka("fruits", "apple").data)
      val eggplant = Hasher.hash(SomeDataTypeFromKafka("vegetables", "eggplant").data)
      val artichoke = Hasher.hash(SomeDataTypeFromKafka("vegetables", "artichoke").data)

      val expected = List(
        Node(
          Hasher.mergeAndHash(banana, apple),
          Some(Node(banana, None, None)),
          Some(Node(apple, None, None))
        ),
        Node(
          Hasher.mergeAndHash(eggplant, artichoke),
          Some(Node(eggplant, None, None)),
          Some(Node(artichoke, None, None))
        )
      )

      assert(nodes == expected)

    }

    "get empty when creating nodes from empty seeds" in {

      val listOfData: List[SomeDataTypeFromKafka] = List()

      val nodes = Chainer(listOfData).createGroups.createSeedNodes(true).getNodes

      assert(nodes.isEmpty)

    }

    "create single node" in {
      val listOfData = List(
        SomeDataTypeFromKafka("vegetables", "eggplant"),
        SomeDataTypeFromKafka("vegetables", "artichoke"),
        SomeDataTypeFromKafka("fruits", "banana"),
        SomeDataTypeFromKafka("fruits", "apple")
      )

      val nodes = Chainer(listOfData)
        .withBalancerFunc(_ => Chainer.getEmptyNodeVal)
        .withMergerFunc(Hasher.mergeAndHash)
        .createGroups
        .createSeedHashes
        .createSeedNodes(true)
        .createNode
        .getNode

      val banana = Hasher.hash(SomeDataTypeFromKafka("fruits", "banana").data)
      val apple = Hasher.hash(SomeDataTypeFromKafka("fruits", "apple").data)
      val eggplant = Hasher.hash(SomeDataTypeFromKafka("vegetables", "eggplant").data)
      val artichoke = Hasher.hash(SomeDataTypeFromKafka("vegetables", "artichoke").data)

      val expected = Option(
        Node(Hasher.mergeAndHash(
          Hasher.mergeAndHash(banana, apple),
          Hasher.mergeAndHash(eggplant, artichoke)
        ), Some(
          Node(
            Hasher.mergeAndHash(banana, apple),
            Some(Node(banana, None, None)),
            Some(Node(apple, None, None))
          )
        ),
          Some(Node(
            Hasher.mergeAndHash(eggplant, artichoke),
            Some(Node(eggplant, None, None)),
            Some(Node(artichoke, None, None))
          )))
      )

      assert(nodes == expected)

    }

    "get empty when creating node from empty seeds" in {

      val listOfData: List[SomeDataTypeFromKafka] = List()

      val node = Chainer(listOfData).createGroups.createSeedNodes(true).createNode.getNode

      assert(node.isEmpty)

    }

    "set and get Zero" in {
      val listOfData = List(
        SomeDataTypeFromKafka("vegetables", "eggplant"),
        SomeDataTypeFromKafka("vegetables", "artichoke"),
        SomeDataTypeFromKafka("fruits", "banana"),
        SomeDataTypeFromKafka("fruits", "apple")
      )

      val zero = Option("Mandarina")

      val chainer = Chainer(listOfData).withHashZero(zero)
      assert(chainer.getZero == zero)

    }

    "compress with even input " in {

      val listOfData = List(
        SomeDataTypeFromKafka("vegetables", "eggplant"),
        SomeDataTypeFromKafka("vegetables", "artichoke"),
        SomeDataTypeFromKafka("fruits", "banana"),
        SomeDataTypeFromKafka("fruits", "apple")
      )

      val (c, _) = Chainer.create(
        listOfData,
        Chainer.CreateConfig[String](
          Some("init-hash"),
          split = true,
          50,
          s => s,
          Hasher.mergeAndHash,
          _ => "balancing-hash"
        )
      )

      val compressed = c.map(x => Chainer.compress(x)).flatMap(_.toList)

      val node = compressed.map(x => Chainer.uncompress(x)(Hasher.mergeAndHash)).flatMap(_.toList)

      assert(c.map(_.getNode).flatMap(_.toList) == node)
      assert(c.map(_.getNode).flatMap(_.toList).map(_.value) == compressed.map(_.root))

    }

    "compress with odd input " in {

      val listOfData = List(
        SomeDataTypeFromKafka("vegetables", "eggplant"),
        SomeDataTypeFromKafka("vegetables", "artichoke"),
        SomeDataTypeFromKafka("fruits", "banana")
      )

      val (c, _) = Chainer.create(listOfData, Chainer.CreateConfig[String](
        Some("init-hash"),
        split = true,
        50,
        s => s,
        Hasher.mergeAndHash,
        _ => "balancing-hash"
      ))

      val compressed = c.map(x => Chainer.compress(x)).flatMap(_.toList)

      val node = compressed.map(x => Chainer.uncompress(x)(Hasher.mergeAndHash)).flatMap(_.toList)

      assert(c.map(_.getNode).flatMap(_.toList) == node)
      assert(c.map(_.getNode).flatMap(_.toList).map(_.value) == compressed.map(_.root))

    }

    "compress with even input with a lot of data when splitting" in {

      val listOfData = (0 to 10000).map(_ => SomeDataTypeFromKafka("fruits", Random.nextString(20))).toList

      val (c, _) = Chainer.create(listOfData, Chainer.CreateConfig[String](
        Some("init-hash"),
        split = true,
        50,
        s => s,
        Hasher.mergeAndHash,
        _ => "balancing-hash"
      ))

      val compressed = c.map(x => Chainer.compress(x)).flatMap(_.toList)

      val node = compressed.map(x => Chainer.uncompress(x)(Hasher.mergeAndHash)).flatMap(_.toList)

      assert(c.map(_.getNode).flatMap(_.toList) == node)
      assert(c.map(_.getNode).flatMap(_.toList).map(_.value) == compressed.map(_.root))

      val normalNodeSizes = c.map(_.getNode).flatMap(_.toList).map(x => EventLogJsonSupport.ToJson[Node[String]](x).toString.length)
      val compressedNodeSizes = compressed.map(x => EventLogJsonSupport.ToJson[CompressedTreeData[String]](x).toString.length)

      assert(normalNodeSizes.zip(compressedNodeSizes).map { case (a, b) => a > b }.forall(x => x))

    }

    "compress with even input with a lot of data " in {

      val listOfData = (0 to 10000).map(_ => SomeDataTypeFromKafka("fruits", Random.nextString(20))).toList

      val (c, _) = Chainer.create(listOfData, Chainer.CreateConfig[String](
        Some("init-hash"),
        split = true,
        50,
        s => s,
        Hasher.mergeAndHash,
        _ => "balancing-hash"
      ))

      val compressed = c.map(x => Chainer.compress(x)).flatMap(_.toList)

      val node = compressed.map(x => Chainer.uncompress(x)(Hasher.mergeAndHash)).flatMap(_.toList)

      assert(c.map(_.getNode).flatMap(_.toList) == node)
      assert(c.map(_.getNode).flatMap(_.toList).map(_.value) == compressed.map(_.root))

      val normalNodeSizes = c.map(_.getNode).flatMap(_.toList).map(x => EventLogJsonSupport.ToJson[Node[String]](x).toString.length)
      val compressedNodeSizes = compressed.map(x => EventLogJsonSupport.ToJson[CompressedTreeData[String]](x).toString.length)

      assert(normalNodeSizes.zip(compressedNodeSizes).map { case (a, b) => a > b }.forall(x => x))

    }

  }
}
