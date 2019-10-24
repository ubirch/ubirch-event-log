package com.ubirch.discovery.model

import com.ubirch.discovery.TestBase
import com.ubirch.discovery.models.{ Edge, Relation, RelationElem, Vertex }
import com.ubirch.models.{ EventLog, LookupKey, Values }
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
    "Vertex" in {
      import Relation.Implicits._

      val a = Vertex.simple("Hola")
        .connectedTo(Vertex.simple("Hello"))
        .through(Edge.simple("spanish-english"))

      val b = (Vertex.simple("Hola") -> Vertex.simple("Hello")) * Edge.simple("spanish-english")
      val c = Relation(Vertex.simple("Hola"), Vertex.simple("Hello"), Edge.simple("spanish-english"))

      assert(a == b)
      assert(b == c)

    }
  }

}
