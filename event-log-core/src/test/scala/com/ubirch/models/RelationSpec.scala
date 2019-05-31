package com.ubirch.models

import com.ubirch.{ Entities, TestBase }

class RelationSpec extends TestBase {

  "Relation" must {
    "addProperty" in {
      val relation = Relation(Vertex("v1"), Vertex("v2")).addProperty("my property key", "my value")
      assert(relation.edge.properties == Map("my property key" -> "my value"))
    }
    "addProperty twice" in {
      val relation = Relation(Vertex("v1"), Vertex("v2"))
        .addProperty("my property key", "my value")
        .addProperty("my property key 2", "my value 2")

      val expectedMap = Map("my property key" -> "my value", "my property key 2" -> "my value 2")
      assert(relation.edge.properties == expectedMap)
      assert(relation.edge == Edge(expectedMap, None))
    }

    "withProperties" in {
      val relation = Relation(Vertex("v1"), Vertex("v2"))
        .addProperty("my property key 2", "my value 2")
        .withProperties(Map("my property key" -> "my value"))

      val expectedMap = Map("my property key" -> "my value")
      assert(relation.edge.properties == expectedMap)
    }

    "addRelationLabel" in {
      val relation = Relation(Vertex("v1"), Vertex("v2")).addRelationLabel("my relation label")
      assert(relation.edge.label == Option("my relation label"))
    }

    "addRelationLabel as option" in {
      val relation = Relation(Vertex("v1"), Vertex("v2")).addRelationLabel(Some("my relation label"))
      assert(relation.edge.label == Option("my relation label"))
    }

    "addOriginLabel" in {
      val relation = Relation(Vertex("v1"), Vertex("v2")).addOriginLabel("my origin label")
      assert(relation.v1.label == Option("my origin label"))
    }

    "addOriginLabel as Option" in {
      val relation = Relation(Vertex("v1"), Vertex("v2")).addOriginLabel(Some("my origin label"))
      assert(relation.v1.label == Option("my origin label"))
    }

    "addTargetLabel" in {
      val relation = Relation(Vertex("v1"), Vertex("v2")).addTargetLabel("my target label")
      assert(relation.v2.label == Option("my target label"))
    }

    "addTargetLabel as Option" in {
      val relation = Relation(Vertex("v1"), Vertex("v2")).addTargetLabel(Some("my target label"))
      assert(relation.v2.label == Option("my target label"))
    }

    "fromEventLog" in {
      val el = Entities.Events.eventExample().addLookupKeys(LookupKey("name", "category", "key", Seq("value")))
      val relations = Relation.fromEventLog(el)

      val expectedRelations = el.lookupKeys.flatMap { x =>
        x.value.map { target =>
          Relation(Vertex(x.key.name), Vertex(target.name))
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
      val relation = Relation(Vertex("v1"), Vertex("v2"))
      assert(relation.edge == Edge.empty)
    }

  }

  "Edge" must {
    "empty" in {
      assert(Edge(Map.empty, None) == Edge.empty)
    }
  }

  "Vertex" must {
    "addLabel" in {
      assert(Vertex("hola", Option("adios"), Map.empty) == Vertex("hola").addLabel("adios"))
    }
    "apply" in {
      assert(Vertex("hola") == Vertex("hola", None, Map.empty))
    }
  }

}
