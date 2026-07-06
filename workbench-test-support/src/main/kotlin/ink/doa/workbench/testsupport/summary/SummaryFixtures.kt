package ink.doa.workbench.testsupport.summary

import ink.doa.workbench.core.common.ids.PublicId
import ink.doa.workbench.core.common.summary.ProjectSummary
import ink.doa.workbench.core.common.summary.TenantSummary
import ink.doa.workbench.core.common.summary.UserSummary

object SummaryFixtures {
  val sampleTenantId: PublicId = PublicId("ten_01JABCDEFGHJKMNPQRSTVWXYZ0")

  val sampleProjectId: PublicId = PublicId("prj_01JABCDEFGHJKMNPQRSTVWXYZ0")

  val sampleUserId: PublicId = PublicId("usr_01JABCDEFGHJKMNPQRSTVWXYZ0")

  fun tenant(
    id: PublicId = sampleTenantId,
    name: String = "Acme",
    slug: String = "acme",
  ): TenantSummary = TenantSummary(id = id, name = name, slug = slug)

  fun project(
    id: PublicId = sampleProjectId,
    identifier: String = "CORE",
    name: String = "Core",
  ): ProjectSummary = ProjectSummary(id = id, identifier = identifier, name = name)

  fun user(
    id: PublicId = sampleUserId,
    displayName: String = "Ada",
    primaryEmail: String? = "ada@example.test",
  ): UserSummary = UserSummary(id = id, displayName = displayName, primaryEmail = primaryEmail)
}
