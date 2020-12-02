package com.ubirch.verification.models

import java.util.{ Date, UUID }

case class AcctEvent(
    id: UUID,
    ownerId: UUID,
    identityId: Option[UUID],
    category: String,
    description: Option[String],
    occurredAt: Date
) {
  def validate: Boolean = identityId.isDefined && description.isDefined
}
