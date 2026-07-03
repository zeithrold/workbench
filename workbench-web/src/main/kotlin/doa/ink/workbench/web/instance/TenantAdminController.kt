package doa.ink.workbench.web.instance

import doa.ink.workbench.service.instance.TenantManagementService
import doa.ink.workbench.web.api.Authenticated
import doa.ink.workbench.web.api.InstanceAdmin
import doa.ink.workbench.web.api.SessionSecured
import doa.ink.workbench.web.api.StandardErrorResponses
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import java.net.URI
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
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
class TenantAdminController(private val service: TenantManagementService) {
  @GetMapping
  @Authenticated
  @InstanceAdmin
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
    slug: String?
  ): List<TenantResponse> = service.list(slug).map { TenantResponse.from(it) }

  @PostMapping
  @Authenticated
  @InstanceAdmin
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
    @Valid @RequestBody request: CreateTenantRequest
  ): ResponseEntity<TenantResponse> {
    val tenant = service.create(request.toCommand())
    val response = TenantResponse.from(tenant)
    return ResponseEntity.created(URI.create("/api/admin/tenants/${response.id}")).body(response)
  }

  @GetMapping("/{id}")
  @Authenticated
  @InstanceAdmin
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
  suspend fun get(@PathVariable id: String): TenantResponse = TenantResponse.from(service.get(id))

  @PatchMapping("/{id}")
  @Authenticated
  @InstanceAdmin
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
}
