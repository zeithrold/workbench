package doa.ink.workbench.core.common.context

import doa.ink.workbench.core.common.ids.PublicId
import doa.ink.workbench.core.identity.model.UserRecord
import java.util.UUID

data class UserContextSummary(
  val id: UUID,
  val publicId: PublicId,
  val displayName: String,
  val primaryEmail: String?,
) {
  companion object {
    fun from(record: UserRecord): UserContextSummary =
      UserContextSummary(
        id = record.id,
        publicId = record.apiId,
        displayName = record.displayName,
        primaryEmail = record.primaryEmail,
      )
  }
}
