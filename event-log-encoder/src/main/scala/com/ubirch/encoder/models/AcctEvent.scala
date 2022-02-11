package com.ubirch.encoder.models

import java.util.{ Date, UUID }

case class AcctEvent(
    id: UUID,
    ownerId: UUID,
    identityId: UUID,
    category: String,
    subCategory: Option[String],
    token: Option[String],
    occurredAt: Date
)
