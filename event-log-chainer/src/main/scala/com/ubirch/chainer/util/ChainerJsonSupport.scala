package com.ubirch.chainer.util

import com.ubirch.models.CustomSerializers
import com.ubirch.util.JsonHelper
import com.ubirch.kafka.formats

object ChainerJsonSupport extends JsonHelper(CustomSerializers.all ++ formats.customSerializers)
