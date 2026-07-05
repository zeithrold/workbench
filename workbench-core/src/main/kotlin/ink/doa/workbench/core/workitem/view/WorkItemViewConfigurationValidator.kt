package ink.doa.workbench.core.workitem.view

import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.workitem.query.BuiltInWorkItemQueryFieldResolver
import ink.doa.workbench.core.workitem.query.WorkItemQuery
import ink.doa.workbench.core.workitem.query.WorkItemQueryFieldResolver
import ink.doa.workbench.core.workitem.query.WorkItemQueryParser
import ink.doa.workbench.core.workitem.query.WorkItemQueryValidator
import java.util.UUID
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject

class WorkItemViewConfigurationValidator(
  private val queryParser: WorkItemQueryParser = WorkItemQueryParser(),
  private val layoutParser: WorkItemViewLayoutParser = WorkItemViewLayoutParser(queryParser),
  private val queryValidator: WorkItemQueryValidator = WorkItemQueryValidator(),
  private val fieldResolver: WorkItemQueryFieldResolver = BuiltInWorkItemQueryFieldResolver,
) {
  fun validateVisibility(projectId: UUID?, visibility: WorkItemViewVisibility) {
    if (projectId == null && visibility == WorkItemViewVisibility.PROJECT) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_VIEW_VISIBILITY_PROJECT_FORBIDDEN,
        "Tenant-scoped views cannot use project visibility.",
      )
    }
  }

  fun validateLayout(
    filterAst: JsonElement,
    sortAst: JsonElement,
    groupAst: JsonElement,
    displayFields: JsonElement,
  ) {
    val where = parseFilter(filterAst)
    val sort = parseSort(sortAst)
    queryValidator.validate(
      WorkItemQuery(
        where = where,
        sort = sort,
      )
    )
    layoutParser.parseGroup(groupAst)?.let { group ->
      fieldResolver.resolve(group.field)
    }
    layoutParser.parseDisplayFields(displayFields).forEach { displayField ->
      fieldResolver.resolve(displayField.field)
    }
  }

  private fun parseFilter(filterAst: JsonElement) =
    when {
      filterAst is JsonNull -> null
      filterAst is JsonObject && filterAst.isEmpty() -> null
      else -> queryParser.parseCondition(filterAst)
    }

  private fun parseSort(sortAst: JsonElement) =
    when {
      sortAst is JsonNull -> emptyList()
      sortAst is JsonArray && sortAst.isEmpty() -> emptyList()
      else -> queryParser.parseSortTerms(sortAst)
    }
}
