package com.ubirch.discovery.util

import com.ubirch.kafka.formats
import com.ubirch.models.CustomSerializers
import com.ubirch.util.JsonHelper

/**
  * Convenience object for managing json conversions.
  * It includes the ProtocolMessage serializer.
  */
object DiscoveryJsonSupport extends JsonHelper(CustomSerializers.all ++ formats.customSerializers)
