package com.ubirch.chainer.util

import com.ubirch.kafka.formats
import com.ubirch.models.CustomSerializers
import com.ubirch.util.JsonHelper

object ChainerJsonSupport extends JsonHelper(CustomSerializers.all ++ formats.customSerializers)
