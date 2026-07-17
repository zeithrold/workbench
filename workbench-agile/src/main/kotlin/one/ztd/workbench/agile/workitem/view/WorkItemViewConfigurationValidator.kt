package one.ztd.workbench.agile.workitem.view

import java.util.UUID
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import one.ztd.workbench.agile.workitem.query.BuiltInWorkItemQueryFieldResolver
import one.ztd.workbench.agile.workitem.query.WorkItemQueryParser
import one.ztd.workbench.agile.workitem.query.WorkItemQueryValidator
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode

class WorkItemViewConfigurationValidator(
  private val queryParser: WorkItemQueryParser = WorkItemQueryParser(),
  private val layoutParser: WorkItemViewLayoutParser = WorkItemViewLayoutParser(queryParser),
  private val queryValidator: WorkItemQueryValidator = WorkItemQueryValidator(),
) {
  fun validateVisibility(projectId: UUID?, visibility: WorkItemViewVisibility) {
    if (projectId == null && visibility == WorkItemViewVisibility.PROJECT) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_VIEW_VISIBILITY_PROJECT_FORBIDDEN,
        "Tenant-scoped views cannot use project visibility.",
      )
    }
  }

  fun validateLayout(queryAst: JsonElement, displayFields: JsonElement) {
    if (queryAst is JsonNull) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_QUERY_OBJECT_REQUIRED,
        "Work item view query is required.",
      )
    }
    queryValidator.validate(queryParser.parse(queryAst))
    layoutParser.parseDisplayFields(displayFields).forEach { displayField ->
      BuiltInWorkItemQueryFieldResolver.resolve(displayField.field)
    }
  }
}
