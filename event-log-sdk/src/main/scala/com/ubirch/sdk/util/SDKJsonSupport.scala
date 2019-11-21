package com.ubirch.sdk.util

import com.ubirch.kafka.formats
import com.ubirch.models.CustomSerializers
import com.ubirch.util.JsonHelper

object SDKJsonSupport extends JsonHelper(CustomSerializers.all ++ formats.customSerializers)
