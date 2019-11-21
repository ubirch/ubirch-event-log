package com.ubirch.util

import com.ubirch.kafka.formats
import com.ubirch.models.CustomSerializers

object TrustCodeJsonSupport extends JsonHelper(CustomSerializers.all ++ formats.customSerializers)