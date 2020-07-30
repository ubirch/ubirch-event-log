package com.ubirch.verification.service.util

import com.ubirch.models.CustomSerializers
import com.ubirch.util.JsonHelper

/**
  * Convenience object for managing json conversions.
  */
object LookupJsonSupport extends JsonHelper(CustomSerializers.all)
