package com.ubirch.verification.microservice.util

import com.ubirch.kafka.formats
import com.ubirch.models.CustomSerializers
import com.ubirch.util.JsonHelper

/**
  * Convenience object for managing json conversions.
  * It includes the ProtocolMessage serializer.
  */
object LookupJsonSupport extends JsonHelper(CustomSerializers.all ++ formats.customSerializers)
