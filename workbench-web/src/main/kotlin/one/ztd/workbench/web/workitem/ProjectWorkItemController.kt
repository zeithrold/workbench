package one.ztd.workbench.web.workitem

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.headers.Header
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.net.URI
import kotlinx.serialization.json.Json
import one.ztd.workbench.agile.workitem.WorkItemDisplayFieldCatalogService
import one.ztd.workbench.agile.workitem.WorkItemFieldOptionService
import one.ztd.workbench.agile.workitem.WorkItemQueryService
import one.ztd.workbench.agile.workitem.WorkItemSearchGroupsPageRequest
import one.ztd.workbench.agile.workitem.WorkItemSearchPageRequest
import one.ztd.workbench.agile.workitem.WorkItemSearchScope
import one.ztd.workbench.agile.workitem.WorkItemService
import one.ztd.workbench.agile.workitem.WorkItemTransitionService
import one.ztd.workbench.agile.workitem.model.DeleteWorkItemCommand
import one.ztd.workbench.agile.workitem.query.WorkItemQueryParser
import one.ztd.workbench.agile.workitem.query.WorkItemSearchGroupScope
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.kernel.common.pagination.WorkItemSearchCursor
import one.ztd.workbench.kernel.common.pagination.WorkItemSearchGroupCursor
import one.ztd.workbench.web.api.Authenticated
import one.ztd.workbench.web.api.AuthenticatedOnly
import one.ztd.workbench.web.api.Authorize
import one.ztd.workbench.web.api.ProjectScoped
import one.ztd.workbench.web.api.ResourceId
import one.ztd.workbench.web.api.SessionSecured
import one.ztd.workbench.web.api.StandardErrorResponses
import one.ztd.workbench.web.api.TenantScoped
import one.ztd.workbench.web.api.context.ProjectRequestContext
import one.ztd.workbench.web.api.http.WORKBENCH_NEXT_CURSOR_HEADER
import one.ztd.workbench.web.api.http.headersIfNextToken
import org.springdoc.core.annotations.ParameterObject
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class WorkItemFieldOptionsParameters(
  val query: String? = null,
  val cursor: String? = null,
  @field:Min(1) @field:Max(100) val limit: Int = 50,
)

@RestController
@RequestMapping("/api/projects/{id}/work-items")
@Tag(name = "Project Work Items", description = "Project-scoped work item runtime APIs.")
@SessionSecured
@StandardErrorResponses
class ProjectWorkItemController(
  private val service: WorkItemService,
  private val transitionService: WorkItemTransitionService,
  private val queryService: WorkItemQueryService,
  private val displayFieldCatalog: WorkItemDisplayFieldCatalogService,
  private val fieldOptions: WorkItemFieldOptionService,
) {
  private val queryParser = WorkItemQueryParser()

  @PostMapping
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "issue.create", resource = "issue")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a work item")
  suspend fun create(
    @Valid @RequestBody request: CreateWorkItemRequest,
    projectContext: ProjectRequestContext,
  ): ResponseEntity<WorkItemResponse> {
    val result = service.create(request.toCommand(projectContext, projectContext.actorUserId()))
    val response = WorkItemResponse.from(result)
    return ResponseEntity.created(
        workItemLocation(projectContext.project.publicId.value, response.id)
      )
      .body(response)
  }

  @PostMapping("/search")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "issue.view", resource = "issue")
  @Operation(
    summary = "Search project work items",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Matching work items",
          headers =
            [
              Header(
                name = WORKBENCH_NEXT_CURSOR_HEADER,
                description = "Cursor for the next page when more results are available.",
                schema = Schema(type = "string"),
              )
            ],
          content =
            [
              Content(
                array = ArraySchema(schema = Schema(implementation = WorkItemResponse::class))
              )
            ],
        )
      ],
  )
  suspend fun search(
    @Valid @RequestBody request: WorkItemSearchRequest,
    projectContext: ProjectRequestContext,
  ): ResponseEntity<List<WorkItemResponse>> {
    val result =
      queryService.search(
        scope = WorkItemSearchScope(projectContext.tenant.id, projectContext.project.id),
        query = queryParser.parse(Json.parseToJsonElement(request.query.toString())),
        groupScope =
          request.scope?.let { queryParser.parseGroupScope(Json.parseToJsonElement(it.toString())) }
            ?: WorkItemSearchGroupScope(),
        page =
          WorkItemSearchPageRequest(
            limit = request.limit ?: 50,
            cursor = request.cursor?.let(WorkItemSearchCursor::decode),
          ),
        actor =
          one.ztd.workbench.agile.workitem.WorkItemSearchActor(
            projectContext.actorUserId(),
            projectContext.actorUserApiId(),
          ),
      )
    val body = result.hits.map(WorkItemResponse::from)
    return ResponseEntity.ok().headersIfNextToken(result.nextCursor?.encode(), body)
  }

  @PostMapping("/search/groups")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "issue.view", resource = "issue")
  @Operation(
    summary = "Search project work item groups",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Matching work item groups",
          content =
            [Content(schema = Schema(implementation = WorkItemSearchGroupsPageResponse::class))],
        )
      ],
  )
  suspend fun searchGroups(
    @Valid @RequestBody request: WorkItemSearchGroupsRequest,
    projectContext: ProjectRequestContext,
  ): WorkItemSearchGroupsPageResponse =
    WorkItemSearchGroupsPageResponse.from(
      queryService.searchGroups(
        scope = WorkItemSearchScope(projectContext.tenant.id, projectContext.project.id),
        query = queryParser.parse(Json.parseToJsonElement(request.query.toString())),
        page =
          WorkItemSearchGroupsPageRequest(
            groupLimit = request.groupLimit ?: 20,
            groupCursor = request.groupCursor?.let(WorkItemSearchGroupCursor::decode),
          ),
      )
    )

  @GetMapping("/create-form")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "issue.create", resource = "issue")
  @Operation(summary = "Get work item create form")
  suspend fun createForm(
    @RequestParam issueTypeId: String,
    projectContext: ProjectRequestContext,
  ): WorkItemCreateFormResponse =
    WorkItemCreateFormResponse.from(
      service.availableCreateForm(
        projectContext.tenant.id,
        projectContext.project.id,
        issueTypeId,
        projectContext.actorUserId(),
      )
    )

  @GetMapping("/display-fields")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "issue.view", resource = "issue")
  @Operation(
    summary = "List work item display fields",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "System fields and configured project properties",
          content =
            [
              Content(
                array =
                  ArraySchema(
                    schema = Schema(implementation = WorkItemDisplayFieldDefinitionResponse::class)
                  )
              )
            ],
        )
      ],
  )
  suspend fun displayFields(
    projectContext: ProjectRequestContext
  ): List<WorkItemDisplayFieldDefinitionResponse> =
    displayFieldCatalog
      .list(projectContext.tenant.id, projectContext.project.id)
      .map(WorkItemDisplayFieldDefinitionResponse::from)

  @GetMapping("/{workItemId}/fields/{fieldKey}/options")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "issue.view", resource = "issue")
  @Operation(
    summary = "List work item field options",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Selectable field options",
          headers =
            [
              Header(
                name = WORKBENCH_NEXT_CURSOR_HEADER,
                description = "Cursor for the next page when more options are available.",
                schema = Schema(type = "string"),
              )
            ],
          content =
            [
              Content(
                array =
                  ArraySchema(schema = Schema(implementation = WorkItemFieldOptionResponse::class))
              )
            ],
        )
      ],
  )
  suspend fun fieldOptions(
    @PathVariable @ResourceId workItemId: String,
    @PathVariable fieldKey: String,
    @Valid @ParameterObject parameters: WorkItemFieldOptionsParameters,
    projectContext: ProjectRequestContext,
  ): ResponseEntity<List<WorkItemFieldOptionResponse>> {
    val page =
      fieldOptions.list(
        one.ztd.workbench.agile.workitem.WorkItemFieldOptionsRequest(
          tenantId = projectContext.tenant.id,
          projectId = projectContext.project.id,
          workItemApiId = workItemId,
          fieldKey = fieldKey,
          actorUserId = projectContext.actorUserId(),
          page =
            one.ztd.workbench.agile.workitem.WorkItemFieldOptionsPageRequest(
              search = parameters.query,
              cursor = parameters.cursor,
              limit = parameters.limit,
            ),
        )
      )
    return ResponseEntity.ok()
      .headersIfNextToken(page.nextCursor, page.items.map(WorkItemFieldOptionResponse::from))
  }

  @GetMapping("/{workItemId}")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "issue.view", resource = "issue")
  @Operation(summary = "Get a work item")
  suspend fun get(
    @PathVariable @ResourceId workItemId: String,
    projectContext: ProjectRequestContext,
  ): WorkItemResponse =
    WorkItemResponse.from(
      service.get(projectContext.tenant.id, projectContext.project.id, workItemId)
    )

  @PatchMapping("/{workItemId}")
  @TenantScoped
  @ProjectScoped
  @AuthenticatedOnly
  @Operation(
    summary = "Update a work item",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Updated work item",
          content = [Content(schema = Schema(implementation = WorkItemResponse::class))],
        )
      ],
  )
  suspend fun update(
    @PathVariable @ResourceId workItemId: String,
    @Valid @RequestBody request: UpdateWorkItemRequest,
    projectContext: ProjectRequestContext,
  ): WorkItemResponse =
    WorkItemResponse.from(
      service.update(request.toCommand(projectContext, workItemId, projectContext.actorUserId()))
    )

  @DeleteMapping("/{workItemId}")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "issue.delete", resource = "issue")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a work item")
  suspend fun delete(
    @PathVariable @ResourceId workItemId: String,
    @RequestBody(required = false) request: DeleteWorkItemRequest?,
    projectContext: ProjectRequestContext,
  ) {
    service.delete(
      DeleteWorkItemCommand(
        tenantId = projectContext.tenant.id,
        projectId = projectContext.project.id,
        workItemApiId = workItemId,
        actorUserId = projectContext.actorUserId(),
        deleteReason = request?.deleteReason,
      )
    )
  }

  @GetMapping("/{workItemId}/transitions")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "issue.transition", resource = "issue")
  @Operation(
    summary = "List available work item transitions",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Available transitions with target status summaries",
          content =
            [
              Content(
                array =
                  ArraySchema(schema = Schema(implementation = WorkItemTransitionResponse::class))
              )
            ],
        )
      ],
  )
  suspend fun transitions(
    @PathVariable @ResourceId workItemId: String,
    projectContext: ProjectRequestContext,
  ): List<WorkItemTransitionResponse> =
    transitionService
      .availableTransitions(
        projectContext.tenant.id,
        projectContext.project.id,
        workItemId,
        projectContext.actorUserId(),
        projectContext.actorUserApiId(),
      )
      .map(WorkItemTransitionResponse::from)

  @PostMapping("/{workItemId}/transitions")
  @Authenticated
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "issue.transition", resource = "issue")
  @Operation(
    summary = "Transition a work item",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Transitioned work item",
          content = [Content(schema = Schema(implementation = WorkItemResponse::class))],
        )
      ],
  )
  suspend fun transition(
    @PathVariable @ResourceId workItemId: String,
    @Valid @RequestBody request: TransitionWorkItemRequest,
    projectContext: ProjectRequestContext,
  ): WorkItemResponse =
    WorkItemResponse.from(
      transitionService.transition(
        request.toCommand(
          projectContext,
          workItemId,
          projectContext.actorUserId(),
          projectContext.actorUserApiId(),
        )
      )
    )
}

private fun ProjectRequestContext.actorUserId() =
  actor?.id ?: throw InvalidRequestException(WorkbenchErrorCode.AUTH_AUTHENTICATED_USER_REQUIRED)

private fun ProjectRequestContext.actorUserApiId() =
  actor?.publicId?.value
    ?: throw InvalidRequestException(WorkbenchErrorCode.AUTH_AUTHENTICATED_USER_REQUIRED)

private fun workItemLocation(projectId: String, workItemId: String): URI =
  URI.create("/api/projects/$projectId/work-items/$workItemId")
