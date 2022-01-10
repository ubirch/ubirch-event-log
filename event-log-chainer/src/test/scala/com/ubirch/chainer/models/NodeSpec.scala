package com.ubirch.chainer.models

import com.ubirch.TestBase

class NodeSpec extends TestBase {

  "A Node" must {
    "allow value and left and right Nodes" in {
      val value = "value"
      val left = Node.empty("left")
      val right = Node.empty("right")

      val node = Node.empty(value).withLeft(left).withRight(right)

      assert(node.value == value)
      assert(node.left.contains(left))
      assert(node.right.contains(right))

      val node1 = Node(value, Some(left), Some(right))

      assert(node1.value == value)
      assert(node1.left.contains(left))
      assert(node1.right.contains(right))

    }

    "allow empty node" in {

      val node = Node.empty(value)
      assert(node.value == value)
      assert(node.left.isEmpty)
      assert(node.right.isEmpty)

    }

    "add left and right node with withs helpers" in {
      val value = "value"
      val left = Node.empty("left")
      val right = Node.empty("right")

      val node = Node.empty(value).withLeft(left).withRight(right)
      val node1 = Node(value, Some(left), Some(right))

      assert(node.value == node1.value)
      assert(node.left == node1.left)
      assert(node.right == node1.right)
    }

    "add new value with WithValue" in {
      val value = "value"
      val node = Node.empty(value).withValue(value.toUpperCase)

      assert(node.value == value.toUpperCase())
      assert(node.value != value)

    }

    "has and is helpers should return as expected" in {
      val value = "value"
      val node = Node.empty(value)

      assert(!node.hasLeft)
      assert(!node.hasRight)
      assert(node.isLast)

      val left = Node.empty("left")
      val right = Node.empty("right")

      val node1 = node.withLeft(left).withRight(right)

      assert(node1.hasLeft)
      assert(node1.hasRight)
      assert(!node1.isLast)

    }

    "map" in {
      val value = "value"
      val node = Node.empty(value)
      assert(node.map(_.toUpperCase).value == node.value.toUpperCase)

      val left = Node.empty("left")
      val right = Node.empty("right")

      val node1 = node.withLeft(left).withRight(right).map(_.toUpperCase)

      assert(node1.value == value.toUpperCase)
      assert(node1.left.map(_.value).contains("left".toUpperCase()))
      assert(node1.right.map(_.value).contains("right".toUpperCase()))

    }

    "balanceRight when odd size" in {

      val balanceWith = Node.empty("foot")

      val n1 = Node.empty("plane")
      val n2 = Node.empty("car")
      val n3 = Node.empty("bike")

      val balanced = Node.balanceRight(balanceWith)(List(n1, n2, n3))

      assert(List(n1, n2, n3, balanceWith) == balanced)
    }

    "not balanceRight when even size" in {

      val balanceWith = Node.empty("foot")

      val n1 = Node.empty("plane")
      val n2 = Node.empty("car")

      val balanced = Node.balanceRight(balanceWith)(List(n1, n2))

      assert(List(n1, n2) == balanced)
    }

    "balanceLeft when odd size" in {

      val balanceWith = Node.empty("foot")

      val n1 = Node.empty("plane")
      val n2 = Node.empty("car")
      val n3 = Node.empty("bike")

      val balanced = Node.balanceLeft(balanceWith)(List(n1, n2, n3))

      assert(List(balanceWith, n1, n2, n3) == balanced)
    }

    "not balanceLeft when even size" in {

      val balanceWith = Node.empty("foot")

      val n1 = Node.empty("plane")
      val n2 = Node.empty("car")

      val balanced = Node.balanceLeft(balanceWith)(List(n1, n2))

      assert(List(n1, n2) == balanced)
    }

    "create nodes from seeds" in {

      val empty: List[String] = List()
      assert(Node.seeds(empty: _*).isEmpty)

      assert(Node.seeds("a", "b", "c") ==
        List(Node.empty("a"), Node.empty("b"), Node.empty("c")))

    }

    "join a single seed" in {

      val nodes = Node.seeds("a").join((a, b) => a + b)
      assert(nodes == List(Node.empty("a")))

    }

    "join with two seeds" in {

      val nodes = Node.seeds("a", "b").join((a, b) => a + b)
      assert(nodes == List(Node("ab", Some(Node("a", None, None)), Some(Node("b", None, None)))))

      assert(nodes.map(_.value) == List("ab"))

    }

    "join with three seeds" in {

      val nodes = Node.seeds("a", "b", "c").join((a, b) => a + b)
      assert(nodes == List(Node("cab", Some(Node("c", None, None)), Some(Node("ab", Some(Node("a", None, None)), Some(Node("b", None, None)))))))

      assert(nodes.map(_.value) == List("cab"))
    }

    "join with four seeds" in {

      val nodes = Node.seeds("a", "b", "c", "d").join((a, b) => a + b)
      assert(nodes == List(Node("abcd", Some(Node("ab", Some(Node("a", None, None)), Some(Node("b", None, None)))), Some(Node("cd", Some(Node("c", None, None)), Some(Node("d", None, None)))))))

      assert(nodes.map(_.value) == List("abcd"))

    }

    "join with the alphabet seeds" in {

      val values = ('a' to 'z').toList.map(_.toString)

      val nodes = Node.seeds(values: _*).join((a, b) => a + b)

      val expected = List(
        Node(
          "uvwxyzabcdefghijklmnopqrst",
          Some(Node(
            "uvwxyzabcd",
            Some(Node(
              "uvwx",
              Some(Node(
                "uv",
                Some(Node("u", None, None)),
                Some(Node("v", None, None))
              )),
              Some(Node(
                "wx",
                Some(Node("w", None, None)),
                Some(Node("x", None, None))
              ))
            )),
            Some(Node(
              "yzabcd",
              Some(Node(
                "yz",
                Some(Node("y", None, None)),
                Some(Node("z", None, None))
              )),
              Some(Node(
                "abcd",
                Some(Node(
                  "ab",
                  Some(Node("a", None, None)),
                  Some(Node("b", None, None))
                )),
                Some(Node(
                  "cd",
                  Some(Node("c", None, None)),
                  Some(Node("d", None, None))
                ))
              ))
            ))
          )),
          Some(Node(
            "efghijklmnopqrst",
            Some(Node(
              "efghijkl",
              Some(Node(
                "efgh",
                Some(Node(
                  "ef",
                  Some(Node("e", None, None)),
                  Some(Node("f", None, None))
                )),
                Some(Node(
                  "gh",
                  Some(Node("g", None, None)),
                  Some(Node("h", None, None))
                ))
              )),
              Some(Node(
                "ijkl",
                Some(Node(
                  "ij",
                  Some(Node("i", None, None)),
                  Some(Node("j", None, None))
                )),
                Some(Node(
                  "kl",
                  Some(Node("k", None, None)),
                  Some(Node("l", None, None))
                ))
              ))
            )),
            Some(Node(
              "mnopqrst",
              Some(Node(
                "mnop",
                Some(Node(
                  "mn",
                  Some(Node("m", None, None)),
                  Some(Node("n", None, None))
                )),
                Some(Node(
                  "op",
                  Some(Node("o", None, None)),
                  Some(Node("p", None, None))
                ))
              )),
              Some(Node("qrst", Some(Node(
                "qr",
                Some(Node("q", None, None)),
                Some(Node("r", None, None))
              )),
                Some(Node(
                  "st",
                  Some(Node("s", None, None)),
                  Some(Node("t", None, None))
                ))))
            ))
          ))
        )
      )

      assert(nodes == expected)

      assert(nodes.map(_.value) == List("uvwxyzabcdefghijklmnopqrst"))

      assert(nodes.headOption.map(x => Node.depth(x)).getOrElse(0) == 4)

    }

    "join2 with the alphabet seeds" in {

      val values = ('a' to 'z').toList.map(_.toString)
      val nodes = Node.seeds(values: _*).join2((a, b) => a + b)

      val expected = List(
        Node(
          "abcdefghijklmnopqrstuvwxyz",
          Some(Node(
            "abcdefghijklmnop",
            Some(Node(
              "abcdefgh",
              Some(Node(
                "abcd",
                Some(Node(
                  "ab",
                  Some(Node("a", None, None)),
                  Some(Node("b", None, None))
                )),
                Some(Node(
                  "cd",
                  Some(Node("c", None, None)),
                  Some(Node("d", None, None))
                ))
              )),
              Some(Node(
                "efgh",
                Some(Node(
                  "ef",
                  Some(Node("e", None, None)),
                  Some(Node("f", None, None))
                )),
                Some(Node(
                  "gh",
                  Some(Node("g", None, None)),
                  Some(Node("h", None, None))
                ))
              ))
            )),
            Some(Node(
              "ijklmnop",
              Some(Node(
                "ijkl",
                Some(Node(
                  "ij",
                  Some(Node("i", None, None)),
                  Some(Node("j", None, None))
                )),
                Some(Node(
                  "kl",
                  Some(Node("k", None, None)),
                  Some(Node("l", None, None))
                ))
              )),
              Some(Node(
                "mnop",
                Some(Node(
                  "mn",
                  Some(Node("m", None, None)),
                  Some(Node("n", None, None))
                )),
                Some(Node(
                  "op",
                  Some(Node("o", None, None)),
                  Some(Node("p", None, None))
                ))
              ))
            ))
          )),
          Some(Node(
            "qrstuvwxyz",
            Some(Node(
              "qrstuvwx",
              Some(Node(
                "qrst",
                Some(Node(
                  "qr",
                  Some(Node("q", None, None)),
                  Some(Node("r", None, None))
                )),
                Some(Node(
                  "st",
                  Some(Node("s", None, None)),
                  Some(Node("t", None, None))
                ))
              )),
              Some(Node(
                "uvwx",
                Some(Node(
                  "uv",
                  Some(Node("u", None, None)),
                  Some(Node("v", None, None))
                )),
                Some(Node(
                  "wx",
                  Some(Node("w", None, None)),
                  Some(Node("x", None, None))
                ))
              ))
            )),
            Some(Node(
              "yz",
              Some(Node("y", None, None)),
              Some(Node("z", None, None))
            ))
          ))
        )
      )

      assert(nodes == expected)

      assert(nodes.map(_.value) == List("abcdefghijklmnopqrstuvwxyz"))

      assert(nodes.headOption.map(x => Node.depth(x)).getOrElse(0) == 5)

    }

    "size" in {
      val values = ('a' to 'b').toList.map(_.toString)
      val nodes = Node.seeds(values: _*).join2((a, b) => a + b)

      assert(nodes.map(_.size) == List(3))
    }

    "size 2" in {
      val values = (1 to 12).toList.map(_.toString)
      val nodes = Node.seeds(values: _*).join2((a, b) => a + b)

      assert(nodes.map(_.size) == List(23))
    }

    "size 3" in {
      val values = (1 to 20).toList.map(_.toString)
      val nodes = Node.seeds(values: _*).join2((a, b) => a + b)

      assert(nodes.map(_.size) == List(39))
    }

  }
}
