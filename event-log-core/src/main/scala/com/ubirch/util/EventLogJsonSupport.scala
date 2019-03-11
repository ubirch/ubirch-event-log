package com.ubirch.util

import com.ubirch.models.CustomSerializers

object EventLogJsonSupport extends JsonHelper(CustomSerializers.all)
