package doa.ink.workbench.service.permission

import doa.ink.workbench.core.permission.model.PermissionCondition

data class PermissionConditionInput(
  val field: String? = null,
  val expected: String? = null,
  val allOf: List<PermissionConditionInput> = emptyList(),
) {
  fun toModel(): PermissionCondition? =
    if (allOf.isNotEmpty()) {
      PermissionCondition.AllOf(allOf.mapNotNull { it.toModel() })
    } else if (field != null && expected != null) {
      PermissionCondition.FieldEquals(field, expected)
    } else {
      null
    }
}
