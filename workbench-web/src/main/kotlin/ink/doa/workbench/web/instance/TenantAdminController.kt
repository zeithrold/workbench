package ink.doa.workbench.web.instance

import ink.doa.workbench.application.identity.PublicIdResolver
import ink.doa.workbench.application.instance.TenantManagementApplicationService
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import ink.doa.workbench.web.api.Authenticated
import ink.doa.workbench.web.api.Authorize
import ink.doa.workbench.web.api.InstanceScoped
import ink.doa.workbench.web.api.SessionSecured
import ink.doa.workbench.web.api.StandardErrorResponses
import ink.doa.workbench.web.api.context.InstanceRequestContext
import ink.doa.workbench.web.api.http.HttpClientContext
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import java.net.URI
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
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

@RestController
@RequestMapping("/api/admin/tenants")
@Tag(
  name = "Tenant Administration",
  description = "Instance-scoped tenant management for system administrators.",
)
@SessionSecured
@StandardErrorResponses
class TenantAdminController(
  private val service: TenantManagementApplicationService,
  private val publicIds: PublicIdResolver,
) {
  @GetMapping
  @Authenticated
  @InstanceScoped
  @Authorize(action = "tenant.read", resource = "tenant")
  @Operation(
    summary = "List tenants",
    description = "Returns all tenants. Optionally filter by slug.",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Matching tenants",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = TenantResponse::class),
              )
            ],
        )
      ],
  )
  suspend fun list(
    @Parameter(description = "Filter by exact tenant slug.", example = "acme")
    @RequestParam(required = false)
    slug: String?,
    @Suppress("UnusedParameter") instanceContext: InstanceRequestContext,
  ): List<TenantResponse> = service.list(slug).map { TenantResponse.from(it) }

  @PostMapping
  @Authenticated
  @InstanceScoped
  @Authorize(action = "tenant.create", resource = "tenant")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
    summary = "Create a tenant",
    description = "Creates a tenant and enables the builtin password login method for it.",
    responses =
      [
        ApiResponse(
          responseCode = "201",
          description = "Tenant created",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = TenantResponse::class),
              )
            ],
        ),
        ApiResponse(
          responseCode = "409",
          description = "Slug already in use",
          content =
            [
              Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
              )
            ],
        ),
      ],
  )
  suspend fun create(
    @Valid @RequestBody request: CreateTenantRequest,
    instanceContext: InstanceRequestContext,
    httpRequest: HttpServletRequest,
  ): ResponseEntity<TenantResponse> {
    val requestHost = HttpClientContext.resolveRequestHost(httpRequest)
    val view =
      service.createWithAdmin(
        command = request.toCommand { publicIds.resolveUser(it).id },
        actorUserId = instanceContext.actor?.id,
        requestHost = requestHost,
      )
    val response = TenantResponse.from(view)
    return ResponseEntity.created(URI.create("/api/admin/tenants/${response.id}")).body(response)
  }

  @GetMapping("/{id}")
  @Authenticated
  @InstanceScoped
  @Authorize(action = "tenant.read", resource = "tenant")
  @Operation(
    summary = "Get a tenant",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Tenant details",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = TenantResponse::class),
              )
            ],
        )
      ],
  )
  suspend fun get(
    @PathVariable id: String,
    @Suppress("UnusedParameter") instanceContext: InstanceRequestContext,
  ): TenantResponse = TenantResponse.from(service.get(id))

  @PatchMapping("/{id}")
  @Authenticated
  @InstanceScoped
  @Authorize(action = "tenant.update", resource = "tenant")
  @Operation(
    summary = "Update a tenant",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Updated tenant",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = TenantResponse::class),
              )
            ],
        )
      ],
  )
  suspend fun patch(
    @PathVariable id: String,
    @Valid @RequestBody request: PatchTenantRequest,
    @Suppress("UnusedParameter") instanceContext: InstanceRequestContext,
  ): TenantResponse =
    TenantResponse.from(
      service.update(
        tenantPublicId = id,
        name = request.name,
        slug = request.slug,
        timezone = request.timezone,
        locale = request.locale,
      )
    )

  @DeleteMapping("/{id}")
  @Authenticated
  @InstanceScoped
  @Authorize(action = "tenant.delete", resource = "tenant")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(
    summary = "Destroy a tenant",
    description =
      "Marks the tenant as destroying and schedules asynchronous cleanup. Returns 202 when accepted.",
    responses =
      [
        ApiResponse(responseCode = "202", description = "Tenant destruction accepted"),
        ApiResponse(
          responseCode = "409",
          description = "Tenant is already being destroyed",
          content =
            [
              Content(
                mediaType = "application/problem+json",
                schema = Schema(implementation = ProblemDetail::class),
              )
            ],
        ),
      ],
  )
  suspend fun destroy(
    @PathVariable id: String,
    @Valid @RequestBody(required = false) request: DestroyTenantRequest?,
    instanceContext: InstanceRequestContext,
  ) {
    service.requestDestroy(
      tenantPublicId = id,
      actorUserId =
        instanceContext.actor?.id
          ?: throw InvalidRequestException(WorkbenchErrorCode.AUTH_AUTHENTICATED_USER_REQUIRED),
      deleteReason = request?.deleteReason,
    )
  }
}
