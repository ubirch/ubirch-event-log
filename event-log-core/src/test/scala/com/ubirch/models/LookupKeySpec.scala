package com.ubirch.models

import com.ubirch.TestBase

class LookupKeySpec extends TestBase {

  "LookupKey" must {

    "withKey" in {
      val lookupKey = LookupKey("name", "category", "key", Seq("value"))
      val newKey = Key("new key", Some("new label"))
      val lookupKey2 = lookupKey.withKey(newKey)
      assert(lookupKey2.key == newKey)
    }

    "withValue" in {
      val lookupKey = LookupKey("name", "category", "key", Seq("value"))
      val newValue = Seq(Value("new value", Some("new label")))
      val lookupKey2 = lookupKey.withValue(newValue)
      assert(lookupKey2.value == newValue)
    }

    "addValue" in {
      val lookupKey = LookupKey("name", "category", "key", Seq("value"))
      val newValue = Value("new value", Some("new label"))
      val lookupKey2 = lookupKey.addValue(newValue)
      assert(lookupKey2.value == lookupKey.value ++ Seq(newValue))
    }

    "addValueLabelForAll" in {
      val lookupKey = LookupKey("name", "category", "key", Seq("value1", "value2"))
        .addValueLabelForAll("my label for all")

      assert(lookupKey.value == Seq(Value("value1", Some("my label for all")), Value("value2", Some("my label for all"))))
    }

    "categoryAsKeyLabel" in {
      val lookupKey = LookupKey("name", "category", "key", Seq("value1", "value2")).categoryAsKeyLabel
      assert(lookupKey.key.label == Option("category"))
    }

    "nameAsValueLabelForAll" in {
      val lookupKey = LookupKey("name", "category", "key", Seq("value1", "value2")).nameAsValueLabelForAll
      assert(lookupKey.value == Seq(Value("value1", Some("name")), Value("value2", Some("name"))))
    }

    "categoryAsValueLabelForAll" in {
      val lookupKey = LookupKey("name", "category", "key", Seq("value1", "value2")).categoryAsValueLabelForAll
      assert(lookupKey.value == Seq(Value("value1", Some("category")), Value("value2", Some("category"))))
    }

    "nameAsKeyLabel" in {
      val lookupKey = LookupKey("name", "category", "key", Seq("value1", "value2")).nameAsKeyLabel
      assert(lookupKey.key.label == Option("name"))
    }

    "applies" in {
      assert(LookupKey("name", "category", "key", Seq("value")) ==
        LookupKey("name", "category", Key("key", None), Seq(Value("value", None))))

      assert(LookupKey("name", "category", ("key", "key-label"), Seq(("value", "value label"))) ==
        LookupKey("name", "category", Key("key", Option("key-label")), Seq(Value("value", Option("value label")))))
    }

  }

  "Key" in {
    val key = Key("name", Option("label"))
    val newKey = key.withLabel("new label")
    assert(newKey.label == Option("new label"))
  }

  "Value" in {
    val value = Value("name", Option("label"))
    val newValue = value.withLabel("new label")
    assert(newValue.label == Option("new label"))
  }

}
