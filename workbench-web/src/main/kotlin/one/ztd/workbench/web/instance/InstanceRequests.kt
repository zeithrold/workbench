package one.ztd.workbench.web.instance

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import one.ztd.workbench.application.instance.CreateTenantView
import one.ztd.workbench.application.instance.InstanceBootstrapView
import one.ztd.workbench.identity.common.summary.LoginMethodSummary
import one.ztd.workbench.identity.common.summary.UserSummary
import one.ztd.workbench.identity.model.BootstrapInstanceAdminCommand
import one.ztd.workbench.identity.model.CreateTenantWithAdminCommand
import one.ztd.workbench.identity.model.TenantAdminAssignment
import one.ztd.workbench.kernel.common.errors.InvalidRequestException
import one.ztd.workbench.kernel.common.errors.WorkbenchErrorCode
import one.ztd.workbench.tenant.model.TenantRecord
import one.ztd.workbench.tenant.model.UpdateTenantCommand
import one.ztd.workbench.web.admin.AdminUserResponse
import one.ztd.workbench.web.identity.LoginResponse

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
  val initialized: Boolean,
  @field:Schema(description = "Whether instance setup requires the configured setup token.")
  val setupTokenRequired: Boolean,
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

@Schema(description = "How the initial tenant administrator is assigned.")
enum class TenantAdminAssignmentMode {
  SELF,
  USER,
  EMAIL_INVITE,
}

@Schema(description = "Initial tenant administrator assignment.")
data class TenantAdminAssignmentRequest(
  @field:Schema(example = "SELF") val mode: TenantAdminAssignmentMode,
  @field:Schema(
    description = "Required when mode is USER.",
    example = "usr_01JABCDEFGHJKMNPQRSTVWXYZ1",
  )
  val userId: String? = null,
  @field:Email
  @field:Schema(description = "Required when mode is EMAIL_INVITE.", example = "admin@example.com")
  val email: String? = null,
  @field:Schema(description = "Optional display name for the invited administrator.")
  val displayName: String? = null,
) {
  suspend fun toAssignment(
    resolveUserId: suspend (String) -> java.util.UUID
  ): TenantAdminAssignment =
    when (mode) {
      TenantAdminAssignmentMode.SELF -> TenantAdminAssignment.SelfAssignment
      TenantAdminAssignmentMode.USER -> {
        val publicId =
          userId?.takeIf { it.isNotBlank() }
            ?: throw InvalidRequestException(WorkbenchErrorCode.INSTANCE_SETUP_USER_ID_REQUIRED)
        TenantAdminAssignment.UserAssignment(resolveUserId(publicId))
      }
      TenantAdminAssignmentMode.EMAIL_INVITE -> {
        val inviteEmail =
          email?.takeIf { it.isNotBlank() }
            ?: throw InvalidRequestException(WorkbenchErrorCode.INSTANCE_SETUP_EMAIL_REQUIRED)
        TenantAdminAssignment.EmailInviteAssignment(
          email = inviteEmail,
          displayName = displayName,
        )
      }
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
  val adminAssignment: TenantAdminAssignmentRequest,
) {
  suspend fun toCommand(
    resolveUserId: suspend (String) -> java.util.UUID
  ): CreateTenantWithAdminCommand =
    CreateTenantWithAdminCommand(
      name = name,
      slug = slug,
      timezone = timezone,
      locale = locale,
      adminAssignment = adminAssignment.toAssignment(resolveUserId),
    )
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
  @field:Schema(example = "ACTIVE") val status: String,
  val admin: AdminUserResponse?,
  val invitationLink: String?,
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
        status = record.status.name,
        admin = null,
        invitationLink = null,
        createdAt = record.createdAt,
        updatedAt = record.updatedAt,
      )

    fun from(view: CreateTenantView): TenantResponse =
      TenantResponse(
        id = view.tenant.apiId.value,
        name = view.tenant.name,
        slug = view.tenant.slug,
        timezone = view.tenant.timezone,
        locale = view.tenant.locale,
        status = view.tenant.status.name,
        admin = view.admin?.let { AdminUserResponse.from(it) },
        invitationLink = view.invitationLink,
        createdAt = view.tenant.createdAt,
        updatedAt = view.tenant.updatedAt,
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

@Schema(description = "Optional reason recorded when destroying a tenant.")
data class DestroyTenantRequest(
  @field:Schema(description = "Human-readable reason for tenant destruction.")
  val deleteReason: String? = null
)
