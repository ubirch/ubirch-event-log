package com.ubirch.discovery.model

import java.util.Date

import com.ubirch.discovery.TestBase
import com.ubirch.discovery.models.{ Edge, Relation, Vertex }
import com.ubirch.models.{ EventLog, LookupKey, Values }
import com.ubirch.util.UUIDHelper
import org.json4s.JsonAST.JString

class RelationSpec extends TestBase {

  "Relation" must {
    "addProperty" in {
      val relation = Relation(Vertex.simple("v1"), Vertex.simple("v2")).addProperty("my property key", "my value")
      assert(relation.edge.properties == Map("my property key" -> "my value"))
    }
    "addProperty twice" in {
      val relation = Relation(Vertex.simple("v1"), Vertex.simple("v2"))
        .addProperty("my property key", "my value")
        .addProperty("my property key 2", "my value 2")

      val expectedMap = Map("my property key" -> "my value", "my property key 2" -> "my value 2")
      assert(relation.edge.properties == expectedMap)
      assert(relation.edge == Edge(None, expectedMap))
    }

    "withProperties" in {
      val relation = Relation(Vertex.simple("v1"), Vertex.simple("v2"))
        .addProperty("my property key 2", "my value 2")
        .withProperties(Map("my property key" -> "my value"))

      val expectedMap = Map("my property key" -> "my value")
      assert(relation.edge.properties == expectedMap)
    }

    "addRelationLabel" in {
      val relation = Relation(Vertex.simple("v1"), Vertex.simple("v2")).addRelationLabel("my relation label")
      assert(relation.edge.label == Option("my relation label"))
    }

    "addRelationLabel as option" in {
      val relation = Relation(Vertex.simple("v1"), Vertex.simple("v2")).addRelationLabel(Some("my relation label"))
      assert(relation.edge.label == Option("my relation label"))
    }

    "addOriginLabel" in {
      val relation = Relation(Vertex.simple("v1"), Vertex.simple("v2")).addOriginLabel("my origin label")
      assert(relation.vFrom.label == Option("my origin label"))
    }

    "addOriginLabel as Option" in {
      val relation = Relation(Vertex.simple("v1"), Vertex.simple("v2")).addOriginLabel(Some("my origin label"))
      assert(relation.vFrom.label == Option("my origin label"))
    }

    "addTargetLabel" in {
      val relation = Relation(Vertex.simple("v1"), Vertex.simple("v2")).addTargetLabel("my target label")
      assert(relation.vTo.label == Option("my target label"))
    }

    "addTargetLabel as Option" in {
      val relation = Relation(Vertex.simple("v1"), Vertex.simple("v2")).addTargetLabel(Some("my target label"))
      assert(relation.vTo.label == Option("my target label"))
    }

    "fromEventLog" in {
      import LookupKey._
      val el = EventLog(JString("Hola")).addLookupKeys(LookupKey("name", "category", "key".asKey, Seq("value".asValue)))
      val relations = Relation.fromEventLog(el)

      val expectedRelations = el.lookupKeys.flatMap { x =>
        x.value.map { target =>
          Relation(Vertex.simple(x.key.name), Vertex.simple(target.name))
            .addOriginLabel(x.key.label)
            .addTargetLabel(target.label)
            .addRelationLabel(x.category)
            .addProperty(Values.CATEGORY_LABEL, x.category)
            .addProperty(Values.NAME_LABEL, x.name)
        }
      }

      assert(expectedRelations == relations)

    }

    "apply" in {
      val relation = Relation(Vertex.simple("v1"), Vertex.simple("v2"))
      assert(relation.edge == Edge.empty)
    }

  }

  "Edge" must {
    "empty" in {
      assert(Edge(None, Map.empty) == Edge.empty)
    }
  }

  "Vertex" must {
    "addLabel" in {
      assert(Vertex(Option("adios"), Map.empty) == Vertex.simple("hola").addLabel("adios"))
    }
    "apply" in {
      assert(Vertex.simple("hola") == Vertex(Some("hola"), Map.empty))
    }
  }

  "Edge" must {
    "addLabel" in {
      assert(Edge(Option("adios"), Map.empty) == Edge.simple("hola").addLabel("adios"))
    }
    "apply" in {
      assert(Edge.simple("hola") == Edge(Some("hola"), Map.empty))
    }
  }

  "Implicits" must {
    "Vertex simple" in {
      import Relation.Implicits._

      val a = Vertex.simple("Hola").connectedTo(Vertex.simple("Hello")).through(Edge.simple("spanish-english"))
      val b = (Vertex.simple("Hola") -> Vertex.simple("Hello")) * Edge.simple("spanish-english")
      val c = Vertex("Hola").connectedTo(Vertex.simple("Hello")).through(Edge("spanish-english"))
      val z = Relation(Vertex.simple("Hola"), Vertex.simple("Hello"), Edge.simple("spanish-english"))

      assert(a == b)
      assert(b == c)
      assert(c == z)

    }
  }

  "Basic Relations" must {
    "create valid Device relation" in {

      import Relation.Implicits._

      val eventId = UUIDHelper.randomUUID
      val signature = UUIDHelper.randomUUID
      val device = UUIDHelper.randomUUID
      val time = new Date()

      val a = Vertex(Values.UPP_CATEGORY)
        .addProperty(Values.HASH -> eventId)
        .addProperty(Values.SIGNATURE -> signature)
        .addProperty(Values.TYPE -> Values.UPP_CATEGORY)
        .addProperty(Values.TIMESTAMP -> time.getTime)
        .connectedTo(
          Vertex(Values.DEVICE_CATEGORY)
            .addProperty(Values.DEVICE_ID -> device)
            .addProperty(Values.TYPE -> Values.DEVICE_CATEGORY)
        )
        .through(Edge(Values.DEVICE_CATEGORY))

      val _a = Relation(
        vFrom = Vertex(Option(Values.UPP_CATEGORY), Map(
          Values.HASH -> eventId,
          Values.SIGNATURE -> signature,
          Values.TYPE -> Values.UPP_CATEGORY,
          Values.TIMESTAMP -> time.getTime
        )),
        vTo = Vertex(Option(Values.DEVICE_CATEGORY), Map(
          Values.DEVICE_ID -> device,
          Values.TYPE -> Values.DEVICE_CATEGORY
        )),
        edge = Edge(Option(Values.DEVICE_CATEGORY), Map.empty)
      )

      assert(a == _a)

    }

    "create valid Chain relation" in {

      import Relation.Implicits._

      val eventId = UUIDHelper.randomUUID
      val signature = UUIDHelper.randomUUID
      val time = new Date()
      val maybeChain = Option(UUIDHelper.randomUUID)

      val maybeRelation2 = maybeChain.map { chain =>
        Vertex(Values.UPP_CATEGORY)
          .addProperty(Values.HASH -> eventId)
          .addProperty(Values.SIGNATURE -> signature)
          .addProperty(Values.TYPE -> Values.UPP_CATEGORY)
          .addProperty(Values.TIMESTAMP -> time.getTime)
          .connectedTo(
            Vertex(Values.UPP_CATEGORY)
              .addProperty(Values.SIGNATURE -> chain)
              .addProperty(Values.TYPE -> Values.UPP_CATEGORY)
          )
          .through(Edge(Values.CHAIN_CATEGORY))
      }

      val _maybeRelation2 = maybeChain.map { chain =>
        Relation(
          vFrom = Vertex(Option(Values.UPP_CATEGORY), Map(
            Values.HASH -> eventId,
            Values.SIGNATURE -> signature,
            Values.TYPE -> Values.UPP_CATEGORY,
            Values.TIMESTAMP -> time.getTime
          )),
          vTo = Vertex(Option(Values.UPP_CATEGORY), Map(
            Values.SIGNATURE -> chain,
            Values.TYPE -> Values.UPP_CATEGORY
          )),
          edge = Edge(Option(Values.CHAIN_CATEGORY), Map.empty)
        )
      }

      assert(maybeRelation2 == _maybeRelation2)

    }

    "upp slave tree foundation" in {

      import Relation.Implicits._

      val eventId = UUIDHelper.randomUUID
      val signature = UUIDHelper.randomUUID
      val time = new Date()
      val hash = UUIDHelper.randomUUID

      val a = Vertex(Values.SLAVE_TREE_CATEGORY)
        .addProperty(Values.HASH -> eventId)
        .addProperty(Values.TYPE -> Values.SLAVE_TREE_CATEGORY)
        .addProperty(Values.TIMESTAMP -> time.getTime)
        .connectedTo(
          Vertex(Values.UPP_CATEGORY)
            .addProperty(Values.HASH -> hash)
            .addProperty(Values.SIGNATURE -> signature)
            .addProperty(Values.TYPE -> Values.UPP_CATEGORY)
        ).through(Edge(Values.SLAVE_TREE_CATEGORY))

      val b = Relation(
        vFrom = Vertex(
          Option(Values.SLAVE_TREE_CATEGORY), Map(
            Values.HASH -> eventId,
            Values.TYPE -> Values.SLAVE_TREE_CATEGORY,
            Values.TIMESTAMP -> time.getTime
          )
        ),
        vTo = Vertex(
          Option(Values.UPP_CATEGORY), Map(
            Values.HASH -> hash,
            Values.SIGNATURE -> signature,
            Values.TYPE -> Values.UPP_CATEGORY
          )
        ),
        edge = Edge(Option(Values.SLAVE_TREE_CATEGORY), Map.empty)
      )

      assert(a == b)

    }

    "slave tree link" in {

      import Relation.Implicits._

      val eventId = UUIDHelper.randomUUID
      val time = new Date()
      val hash = UUIDHelper.randomUUID

      val a = Vertex(Values.SLAVE_TREE_CATEGORY)
        .addProperty(Values.HASH -> eventId)
        .addProperty(Values.TYPE -> Values.SLAVE_TREE_CATEGORY)
        .addProperty(Values.TIMESTAMP -> time.getTime)
        .connectedTo(
          Vertex(Values.SLAVE_TREE_CATEGORY)
            .addProperty(Values.HASH -> hash)
            .addProperty(Values.TYPE -> Values.SLAVE_TREE_CATEGORY)
        ).through(Edge(Values.SLAVE_TREE_CATEGORY))

      val b =
        Relation(
          vFrom = Vertex(
            Option(Values.SLAVE_TREE_CATEGORY), Map(
              Values.HASH -> eventId,
              Values.TYPE -> Values.SLAVE_TREE_CATEGORY,
              Values.TIMESTAMP -> time.getTime
            )
          ),
          vTo = Vertex(
            Option(Values.SLAVE_TREE_CATEGORY), Map(
              Values.HASH -> hash,
              Values.TYPE -> Values.SLAVE_TREE_CATEGORY
            )
          ),
          edge = Edge(Option(Values.SLAVE_TREE_CATEGORY), Map.empty)
        )

      assert(a == b)

    }

    "master tree slave links" in {

      import Relation.Implicits._

      val eventId = UUIDHelper.randomUUID
      val time = new Date()
      val hash = UUIDHelper.randomUUID

      val a = Vertex(Values.MASTER_TREE_CATEGORY)
        .addProperty(Values.HASH -> eventId)
        .addProperty(Values.TYPE -> Values.MASTER_TREE_CATEGORY)
        .addProperty(Values.TIMESTAMP -> time.getTime)
        .connectedTo(
          Vertex(Values.SLAVE_TREE_CATEGORY)
            .addProperty(Values.HASH -> hash)
            .addProperty(Values.TYPE -> Values.SLAVE_TREE_CATEGORY)
        )
        .through(Edge(Values.MASTER_TREE_CATEGORY))

      val b =
        Relation(
          vFrom = Vertex(Option(Values.MASTER_TREE_CATEGORY), Map(
            Values.HASH -> eventId,
            Values.TYPE -> Values.MASTER_TREE_CATEGORY,
            Values.TIMESTAMP -> time.getTime
          )),
          vTo = Vertex(Option(Values.SLAVE_TREE_CATEGORY), Map(
            Values.HASH -> hash,
            Values.TYPE -> Values.SLAVE_TREE_CATEGORY
          )),
          edge = Edge(Option(Values.MASTER_TREE_CATEGORY), Map.empty)
        )

      assert(a == b)

    }

    "master master" in {

      import Relation.Implicits._

      val eventId = UUIDHelper.randomUUID
      val hash = UUIDHelper.randomUUID

      val a = Vertex(Values.MASTER_TREE_CATEGORY)
        .addProperty(Values.HASH -> eventId)
        .addProperty(Values.TYPE -> Values.MASTER_TREE_CATEGORY)
        .connectedTo(
          Vertex(Values.MASTER_TREE_CATEGORY)
            .addProperty(Values.HASH -> hash)
            .addProperty(Values.TYPE -> Values.MASTER_TREE_CATEGORY)
        ).through(Edge(Values.MASTER_TREE_CATEGORY))

      val b = Relation(
        vFrom = Vertex(
          Option(Values.MASTER_TREE_CATEGORY), Map(
            Values.HASH -> eventId,
            Values.TYPE -> Values.MASTER_TREE_CATEGORY
          )
        ),
        vTo = Vertex(
          Option(Values.MASTER_TREE_CATEGORY), Map(
            Values.HASH -> hash,
            Values.TYPE -> Values.MASTER_TREE_CATEGORY
          )
        ),
        edge = Edge(Option(Values.MASTER_TREE_CATEGORY), Map.empty)
      )

      assert(a == b)

    }

    "public blockchains" in {

      import Relation.Implicits._

      val eventId = UUIDHelper.randomUUID
      val hash = UUIDHelper.randomUUID
      val time = new Date()

      val category = UUIDHelper.randomUUID

      val a = Vertex(Values.PUBLIC_CHAIN_CATEGORY)
        .addProperty(Values.HASH -> eventId)
        .addProperty(Values.TYPE -> Values.PUBLIC_CHAIN_CATEGORY)
        .addProperty(Values.PUBLIC_CHAIN_CATEGORY -> category.toString)
        .addProperty(Values.TIMESTAMP -> time.getTime)
        .connectedTo(
          Vertex(Values.MASTER_TREE_CATEGORY)
            .addProperty(Values.HASH -> hash)
            .addProperty(Values.TYPE -> Values.MASTER_TREE_CATEGORY)
        )
        .through(Edge(Values.PUBLIC_CHAIN_CATEGORY))

      val b =
        Relation(
          vFrom = Vertex(Option(Values.PUBLIC_CHAIN_CATEGORY), Map(
            Values.HASH -> eventId,
            Values.TYPE -> Values.PUBLIC_CHAIN_CATEGORY,
            Values.PUBLIC_CHAIN_CATEGORY -> category.toString,
            Values.TIMESTAMP -> time.getTime
          )),
          vTo = Vertex(Option(Values.MASTER_TREE_CATEGORY), Map(
            Values.HASH -> hash,
            Values.TYPE -> Values.MASTER_TREE_CATEGORY
          )),
          edge = Edge(Option(Values.PUBLIC_CHAIN_CATEGORY), Map.empty)
        )

      assert(a == b)

    }

  }

}
