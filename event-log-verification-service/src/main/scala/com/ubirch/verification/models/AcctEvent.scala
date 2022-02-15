package com.ubirch.verification.models

import java.util.{ Date, UUID }

case class AcctEvent(
    id: UUID,
    ownerId: UUID,
    identityId: UUID,
    category: String,
    subCategory: Option[String],
    externalId: Option[String],
    occurredAt: Date
) {
  def validate: Boolean = category.nonEmpty && subCategory.forall(_.nonEmpty) && externalId.forall(_.length <= 36)
}
