package com.ubirch.discovery.model

import com.ubirch.discovery.models.{ Relation, RelationElem }
import com.ubirch.lookup.TestBase
import com.ubirch.models.{ EventLog, LookupKey, Values }
import org.json4s.JsonAST.JString

class RelationSpec extends TestBase {

  "Relation" must {
    "addProperty" in {
      val relation = Relation(RelationElem.simple("v1"), RelationElem.simple("v2")).addProperty("my property key", "my value")
      assert(relation.edge.properties == Map("my property key" -> "my value"))
    }
    "addProperty twice" in {
      val relation = Relation(RelationElem.simple("v1"), RelationElem.simple("v2"))
        .addProperty("my property key", "my value")
        .addProperty("my property key 2", "my value 2")

      val expectedMap = Map("my property key" -> "my value", "my property key 2" -> "my value 2")
      assert(relation.edge.properties == expectedMap)
      assert(relation.edge == RelationElem(None, expectedMap))
    }

    "withProperties" in {
      val relation = Relation(RelationElem.simple("v1"), RelationElem.simple("v2"))
        .addProperty("my property key 2", "my value 2")
        .withProperties(Map("my property key" -> "my value"))

      val expectedMap = Map("my property key" -> "my value")
      assert(relation.edge.properties == expectedMap)
    }

    "addRelationLabel" in {
      val relation = Relation(RelationElem.simple("v1"), RelationElem.simple("v2")).addRelationLabel("my relation label")
      assert(relation.edge.label == Option("my relation label"))
    }

    "addRelationLabel as option" in {
      val relation = Relation(RelationElem.simple("v1"), RelationElem.simple("v2")).addRelationLabel(Some("my relation label"))
      assert(relation.edge.label == Option("my relation label"))
    }

    "addOriginLabel" in {
      val relation = Relation(RelationElem.simple("v1"), RelationElem.simple("v2")).addOriginLabel("my origin label")
      assert(relation.vFrom.label == Option("my origin label"))
    }

    "addOriginLabel as Option" in {
      val relation = Relation(RelationElem.simple("v1"), RelationElem.simple("v2")).addOriginLabel(Some("my origin label"))
      assert(relation.vFrom.label == Option("my origin label"))
    }

    "addTargetLabel" in {
      val relation = Relation(RelationElem.simple("v1"), RelationElem.simple("v2")).addTargetLabel("my target label")
      assert(relation.vTo.label == Option("my target label"))
    }

    "addTargetLabel as Option" in {
      val relation = Relation(RelationElem.simple("v1"), RelationElem.simple("v2")).addTargetLabel(Some("my target label"))
      assert(relation.vTo.label == Option("my target label"))
    }

    "fromEventLog" in {
      import LookupKey._
      val el = EventLog(JString("Hola")).addLookupKeys(LookupKey("name", "category", "key".asKey, Seq("value".asValue)))
      val relations = Relation.fromEventLog(el)

      val expectedRelations = el.lookupKeys.flatMap { x =>
        x.value.map { target =>
          Relation(RelationElem.simple(x.key.name), RelationElem.simple(target.name))
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
      val relation = Relation(RelationElem.simple("v1"), RelationElem.simple("v2"))
      assert(relation.edge == RelationElem.empty)
    }

  }

  "Edge" must {
    "empty" in {
      assert(RelationElem(None, Map.empty) == RelationElem.empty)
    }
  }

  "Vertex" must {
    "addLabel" in {
      assert(RelationElem(Option("adios"), Map.empty) == RelationElem.simple("hola").addLabel("adios"))
    }
    "apply" in {
      assert(RelationElem.simple("hola") == RelationElem(Some("hola"), Map.empty))
    }
  }

}
