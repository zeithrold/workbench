package ink.doa.workbench.web.project

import ink.doa.workbench.agile.project.ProjectMemberPolicyView
import ink.doa.workbench.agile.project.ProjectMemberService
import ink.doa.workbench.agile.project.ProjectMemberView
import ink.doa.workbench.agile.project.ProjectPermissionPolicySummary
import ink.doa.workbench.core.common.context.ProjectRequestContext
import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import ink.doa.workbench.core.common.summary.UserSummary
import ink.doa.workbench.web.api.Authenticated
import ink.doa.workbench.web.api.Authorize
import ink.doa.workbench.web.api.ProjectScoped
import ink.doa.workbench.web.api.SessionSecured
import ink.doa.workbench.web.api.StandardErrorResponses
import ink.doa.workbench.web.api.TenantScoped
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/projects/{id}")
@Authenticated
@TenantScoped
@ProjectScoped
@SessionSecured
@StandardErrorResponses
@Tag(name = "Project Members", description = "Project member and permission facade.")
class ProjectMemberController(private val service: ProjectMemberService) {
  @GetMapping("/members")
  @Authorize(action = "project.manage", resource = "project")
  @Operation(summary = "List project members")
  suspend fun listMembers(projectContext: ProjectRequestContext): List<ProjectMemberResponse> =
    service.listMembers(projectContext.tenant.id, projectContext.project.id).map {
      ProjectMemberResponse.from(it)
    }

  @PostMapping("/members")
  @Authorize(action = "project.manage", resource = "project")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Add project member")
  suspend fun addMember(
    @Valid @RequestBody request: AddProjectMemberRequest,
    projectContext: ProjectRequestContext,
  ): ProjectMemberResponse =
    ProjectMemberResponse.from(
      service.addMember(
        tenantId = projectContext.tenant.id,
        projectId = projectContext.project.id,
        userPublicId = request.userId,
        policyPublicId = request.policyId,
        role = request.role,
        actorUserId = projectContext.actor?.id,
      )
    )

  @PostMapping("/members/{userId}/policies")
  @Authorize(action = "project.manage", resource = "project")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Attach policy to project member")
  suspend fun attachPolicy(
    @PathVariable userId: String,
    @Valid @RequestBody request: AttachProjectPolicyRequest,
    projectContext: ProjectRequestContext,
  ): ProjectMemberResponse =
    ProjectMemberResponse.from(
      service.attachPolicy(
        tenantId = projectContext.tenant.id,
        projectId = projectContext.project.id,
        userPublicId = userId,
        policyPublicId = request.policyId,
        role = request.role,
        actorUserId = projectContext.actor?.id,
      )
    )

  @DeleteMapping("/members/{userId}/policies/{bindingId}")
  @Authorize(action = "project.manage", resource = "project")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Remove policy from project member")
  suspend fun removePolicy(
    @PathVariable bindingId: String,
    projectContext: ProjectRequestContext,
  ) {
    service.removePolicy(projectContext.tenant.id, bindingId)
  }

  @PostMapping("/join")
  @Authorize(action = "project.join", resource = "project")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Join an open project")
  suspend fun join(projectContext: ProjectRequestContext): ProjectMemberResponse {
    val actorUserId =
      projectContext.actor?.id
        ?: throw InvalidRequestException(WorkbenchErrorCode.AUTH_AUTHENTICATED_USER_REQUIRED)
    return ProjectMemberResponse.from(
      service.join(
        tenantId = projectContext.tenant.id,
        projectId = projectContext.project.id,
        userId = actorUserId,
        actorUserId = actorUserId,
      )
    )
  }
}

data class AddProjectMemberRequest(
  @field:NotBlank val userId: String,
  val policyId: String? = null,
  val role: String? = null,
)

data class AttachProjectPolicyRequest(
  val policyId: String? = null,
  val role: String? = null,
)

data class ProjectMemberResponse(
  val user: UserSummary,
  val policies: List<ProjectMemberPolicyResponse>,
) {
  companion object {
    fun from(view: ProjectMemberView) =
      ProjectMemberResponse(
        user = view.user,
        policies = view.policies.map { ProjectMemberPolicyResponse.from(it) },
      )
  }
}

data class ProjectMemberPolicyResponse(
  val bindingId: String,
  val policy: PermissionPolicySummaryResponse,
) {
  companion object {
    fun from(view: ProjectMemberPolicyView) =
      ProjectMemberPolicyResponse(
        bindingId = view.bindingId,
        policy = PermissionPolicySummaryResponse.from(view.policy),
      )
  }
}

data class PermissionPolicySummaryResponse(
  val id: String,
  val code: String,
  val name: String,
) {
  companion object {
    fun from(summary: ProjectPermissionPolicySummary) =
      PermissionPolicySummaryResponse(
        id = summary.id,
        code = summary.code,
        name = summary.name,
      )
  }
}
