package com.ubirch.verification.models

import java.util.{ Date, UUID }

case class AcctEvent(
    id: UUID,
    ownerId: UUID,
    identityId: Option[UUID],
    category: String,
    subCategory: Option[String],
    token: Option[String],
    occurredAt: Date
)
