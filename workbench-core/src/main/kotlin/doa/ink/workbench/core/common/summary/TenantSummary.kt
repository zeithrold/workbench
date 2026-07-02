package doa.ink.workbench.core.common.summary

import doa.ink.workbench.core.identity.model.TenantRecord

data class TenantSummary(
  val id: String,
  val name: String,
  val slug: String,
) {
  companion object {
    fun from(record: TenantRecord): TenantSummary =
      TenantSummary(
        id = record.apiId.value,
        name = record.name,
        slug = record.slug,
      )
  }
}
