package one.ztd.workbench.identity.common.summary

import one.ztd.workbench.identity.model.UserRecord
import one.ztd.workbench.kernel.common.ids.PublicId

/**
 * Wire-safe user embed. [id] is a typed public id (`usr_` + ULID). Prefer [from] for production
 * mapping.
 */
data class UserSummary(
  val id: PublicId,
  val displayName: String,
  val primaryEmail: String?,
) {
  companion object {
    fun from(record: UserRecord): UserSummary =
      UserSummary(
        id = record.apiId,
        displayName = record.displayName,
        primaryEmail = record.primaryEmail,
      )
  }
}
