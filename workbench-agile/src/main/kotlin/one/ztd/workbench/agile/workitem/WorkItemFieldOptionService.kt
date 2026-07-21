package one.ztd.workbench.agile.workitem

import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import one.ztd.workbench.agile.workitem.model.WorkItemPropertyDataType
import one.ztd.workbench.agile.workitem.template.TemplateField
import one.ztd.workbench.identity.UserRepository
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.PermissionDeniedException
import one.ztd.workbench.kernel.common.errors.ResourceNotFoundException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import org.springframework.stereotype.Service

enum class WorkItemFieldOptionKind {
  USER,
  PRIORITY,
  SPRINT,
  FIXED,
  PROJECT,
  WORK_ITEM,
}

data class WorkItemFieldOption(
  val id: String,
  val label: String,
  val description: String? = null,
  val color: String? = null,
  val icon: String? = null,
  val status: String? = null,
)

data class WorkItemFieldOptionPage(
  val items: List<WorkItemFieldOption>,
  val nextCursor: String?,
)

data class WorkItemFieldOptionQuery(
  val tenantId: UUID,
  val projectId: UUID,
  val propertyId: UUID? = null,
  val kind: WorkItemFieldOptionKind,
  val search: String?,
  val offset: Int,
  val limit: Int,
)

data class WorkItemFieldOptionsRequest(
  val tenantId: UUID,
  val projectId: UUID,
  val workItemApiId: String,
  val fieldKey: String,
  val actorUserId: UUID,
  val page: WorkItemFieldOptionsPageRequest,
)

data class WorkItemFieldOptionsPageRequest(
  val search: String?,
  val cursor: String?,
  val limit: Int,
)

interface WorkItemFieldOptionRepository {
  suspend fun list(query: WorkItemFieldOptionQuery): List<WorkItemFieldOption>
}

@Service
class WorkItemFieldOptionService(
  private val workItems: WorkItemRepository,
  private val users: UserRepository,
  private val contextLoader: WorkItemTransitionContextLoader,
  private val permissions: WorkItemFieldPermissionService,
  private val options: WorkItemFieldOptionRepository,
) {
  suspend fun list(request: WorkItemFieldOptionsRequest): WorkItemFieldOptionPage {
    val limit = request.page.limit
    validateLimit(limit)
    val issue = requireIssue(request)
    val actorApiId = requireActorApiId(request.actorUserId)
    val context = contextLoader.load(issue, request.actorUserId, actorApiId)
    val selection = resolveSelection(context, request.fieldKey)
    assertEditable(context, selection)
    val offset = decodeCursor(request.page.cursor)
    val rows = options.list(optionQuery(request, selection, offset, limit))
    val hasNext = rows.size > limit
    return WorkItemFieldOptionPage(
      items = rows.take(limit),
      nextCursor = if (hasNext) encodeCursor(offset + limit) else null,
    )
  }

  private fun validateLimit(limit: Int) {
    if (limit !in 1..100) {
      throw InvalidRequestException(
        WorkbenchErrorCode.WORK_ITEM_PROPERTY_VALUE_INVALID,
        "Option limit must be between 1 and 100.",
      )
    }
  }

  private suspend fun requireIssue(request: WorkItemFieldOptionsRequest) =
    workItems.findByApiId(request.tenantId, request.projectId, request.workItemApiId)
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_WORK_ITEM_NOT_FOUND)

  private suspend fun requireActorApiId(actorUserId: UUID): String =
    users.findById(actorUserId)?.apiId?.value
      ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND)

  private suspend fun assertEditable(
    context: WorkItemTransitionContext,
    selection: OptionSelection,
  ) {
    if (
      !permissions
        .resolvePatchPolicy(context.permissionContext, selection.field)
        .allowsPatchSubmission()
    ) {
      throw PermissionDeniedException(WorkbenchErrorCode.WORK_ITEM_FIELD_WRITE_DENIED)
    }
  }

  private fun optionQuery(
    request: WorkItemFieldOptionsRequest,
    selection: OptionSelection,
    offset: Int,
    limit: Int,
  ) =
    WorkItemFieldOptionQuery(
      tenantId = request.tenantId,
      projectId = request.projectId,
      propertyId = selection.propertyId,
      kind = selection.kind,
      search = request.page.search?.trim()?.takeIf { it.isNotEmpty() },
      offset = offset,
      limit = limit + 1,
    )

  private fun resolveSelection(
    context: WorkItemTransitionContext,
    fieldKey: String,
  ): OptionSelection =
    when (fieldKey) {
      "assignee" -> OptionSelection(TemplateField.System("assignee"), WorkItemFieldOptionKind.USER)
      "priority" ->
        OptionSelection(TemplateField.System("priority"), WorkItemFieldOptionKind.PRIORITY)
      "sprint" -> OptionSelection(TemplateField.System("sprint"), WorkItemFieldOptionKind.SPRINT)
      else -> resolvePropertySelection(context, fieldKey)
    }

  private fun resolvePropertySelection(
    context: WorkItemTransitionContext,
    fieldKey: String,
  ): OptionSelection {
    val code =
      fieldKey.removePrefix("property.").takeIf { fieldKey.startsWith("property.") }
        ?: throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_MUTATION_UNEXPECTED_FIELD)
    val property =
      context.config.properties.singleOrNull { it.code == code }
        ?: throw InvalidRequestException(WorkbenchErrorCode.WORK_ITEM_PROPERTY_UNAVAILABLE)
    val kind =
      when (property.dataType) {
        WorkItemPropertyDataType.SINGLE_SELECT,
        WorkItemPropertyDataType.MULTI_SELECT -> WorkItemFieldOptionKind.FIXED
        WorkItemPropertyDataType.USER,
        WorkItemPropertyDataType.MULTI_USER -> WorkItemFieldOptionKind.USER
        WorkItemPropertyDataType.PROJECT -> WorkItemFieldOptionKind.PROJECT
        WorkItemPropertyDataType.ISSUE -> WorkItemFieldOptionKind.WORK_ITEM
        else ->
          throw InvalidRequestException(
            WorkbenchErrorCode.WORK_ITEM_PROPERTY_VALUE_INVALID,
            "Field does not use selectable options: $fieldKey",
          )
      }
    return OptionSelection(
      field = TemplateField.Property(property.propertyApiId.value, property.code),
      kind = kind,
      propertyId = property.propertyId,
    )
  }

  private fun decodeCursor(cursor: String?): Int {
    if (cursor == null) return 0
    return runCatching {
        String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).toInt()
      }
      .getOrElse {
        throw InvalidRequestException(
          WorkbenchErrorCode.WORK_ITEM_PROPERTY_VALUE_INVALID,
          "Invalid option cursor.",
        )
      }
  }

  private fun encodeCursor(offset: Int): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(offset.toString().toByteArray())

  private data class OptionSelection(
    val field: TemplateField,
    val kind: WorkItemFieldOptionKind,
    val propertyId: UUID? = null,
  )
}
