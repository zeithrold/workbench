package doa.ink.workbench.web.instance

import doa.ink.workbench.core.common.summary.LoginMethodSummary
import doa.ink.workbench.core.common.summary.UserSummary
import doa.ink.workbench.core.identity.model.BootstrapInstanceAdminCommand
import doa.ink.workbench.core.identity.model.CreateTenantCommand
import doa.ink.workbench.core.identity.model.TenantRecord
import doa.ink.workbench.core.identity.model.UpdateTenantCommand
import doa.ink.workbench.service.instance.InstanceBootstrapView
import doa.ink.workbench.web.identity.LoginResponse
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime

@Schema(description = "Instance bootstrap request for the first system administrator.")
data class InstanceSetupRequest(
  @field:NotBlank @field:Schema(example = "Admin") val displayName: String,
  @field:NotBlank @field:Email @field:Schema(example = "admin@example.com") val email: String,
  @field:NotBlank
  @field:Size(min = 12, max = 128)
  @field:Schema(description = "Initial administrator password.")
  val password: String,
  @field:Schema(description = "Required when workbench.instance.setup-token is configured.")
  val setupToken: String? = null,
) {
  fun toCommand(ipAddress: String?, userAgent: String?): BootstrapInstanceAdminCommand =
    BootstrapInstanceAdminCommand(
      displayName = displayName,
      email = email,
      password = password,
      setupToken = setupToken,
      ipAddress = ipAddress,
      userAgent = userAgent,
    )
}

@Schema(description = "Instance initialization status.")
data class InstanceSetupStatusResponse(
  @field:Schema(description = "Whether a system administrator already exists.")
  val initialized: Boolean
)

@Schema(
  description = "Successful instance bootstrap payload. Also sets the WORKBENCH_SESSION cookie."
)
data class InstanceBootstrapResponse(
  @field:Schema(description = "Created system administrator.") val user: UserSummary,
  @field:Schema(description = "Password login method to use for subsequent sign-in.")
  val loginMethod: LoginMethodSummary,
  @field:Schema(description = "Established session details.") val session: LoginResponse,
) {
  companion object {
    fun from(view: InstanceBootstrapView): InstanceBootstrapResponse =
      InstanceBootstrapResponse(
        user = view.user,
        loginMethod = view.loginMethod,
        session = LoginResponse.from(view.session),
      )
  }
}

@Schema(description = "Fields for creating a tenant.")
data class CreateTenantRequest(
  @field:NotBlank @field:Schema(example = "Acme") val name: String,
  @field:NotBlank
  @field:Pattern(regexp = "^[a-z][a-z0-9-]{1,48}$")
  @field:Schema(example = "acme")
  val slug: String,
  @field:NotBlank @field:Schema(example = "UTC") val timezone: String = "UTC",
  @field:NotBlank @field:Schema(example = "en-US") val locale: String = "en-US",
) {
  fun toCommand(): CreateTenantCommand =
    CreateTenantCommand(name = name, slug = slug, timezone = timezone, locale = locale)
}

@Schema(description = "Partial tenant update. Omitted fields are unchanged.")
data class PatchTenantRequest(
  @field:Schema(example = "Acme Corp") val name: String? = null,
  @field:Pattern(regexp = "^[a-z][a-z0-9-]{1,48}$")
  @field:Schema(example = "acme")
  val slug: String? = null,
  @field:Schema(example = "UTC") val timezone: String? = null,
  @field:Schema(example = "en-US") val locale: String? = null,
)

@Schema(description = "Tenant metadata managed at instance scope.")
data class TenantResponse(
  @field:Schema(example = "ten_01JABCDEFGHJKMNPQRSTVWXYZ0") val id: String,
  val name: String,
  val slug: String,
  val timezone: String,
  val locale: String,
  val createdAt: OffsetDateTime?,
  val updatedAt: OffsetDateTime?,
) {
  companion object {
    fun from(record: TenantRecord): TenantResponse =
      TenantResponse(
        id = record.apiId.value,
        name = record.name,
        slug = record.slug,
        timezone = record.timezone,
        locale = record.locale,
        createdAt = record.createdAt,
        updatedAt = record.updatedAt,
      )
  }
}

fun PatchTenantRequest.toCommand(tenantId: java.util.UUID): UpdateTenantCommand =
  UpdateTenantCommand(
    tenantId = tenantId,
    name = name,
    slug = slug,
    timezone = timezone,
    locale = locale,
  )
