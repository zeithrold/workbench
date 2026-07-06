package ink.doa.workbench.core.workitem.template

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode

fun parseTemplateFieldPath(path: String): TemplateField =
  if (path.startsWith("property.")) {
    val identity =
      path.removePrefix("property.").ifBlank {
        throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_TEMPLATE_PROPERTY_IDENTITY_REQUIRED
        )
      }
    if (identity.startsWith("fld_")) {
      TemplateField.Property(apiId = identity, code = null)
    } else {
      TemplateField.Property(apiId = null, code = identity)
    }
  } else {
    TemplateField.System(path)
  }

fun normalizePropertyKey(key: String): String =
  if (key.startsWith("property.") || TemplateSystemFields.isWritableSystemField(key)) {
    key
  } else {
    "property.$key"
  }

fun fieldPathFromPropertyKey(key: String): TemplateField =
  parseTemplateFieldPath(normalizePropertyKey(key))
