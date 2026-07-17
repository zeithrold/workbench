package one.ztd.workbench.web.identity

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import one.ztd.workbench.identity.UserPreferenceService
import one.ztd.workbench.identity.UserPreferencesView
import one.ztd.workbench.identity.model.AuthenticatedPrincipal
import one.ztd.workbench.web.api.AuthenticatedOnly
import one.ztd.workbench.web.api.SessionSecured
import one.ztd.workbench.web.api.StandardErrorResponses
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users/me/preferences")
@Tag(name = "User Preferences", description = "Preferences owned by the authenticated user.")
@AuthenticatedOnly
@SessionSecured
@StandardErrorResponses
class UserPreferencesController(private val service: UserPreferenceService) {
  @PatchMapping
  @Operation(
    summary = "Update user preferences",
    description = "Sets the personal locale override. Use null to follow the active tenant.",
    responses =
      [
        ApiResponse(
          responseCode = "200",
          description = "Updated user preferences",
          content =
            [
              Content(
                mediaType = "application/json",
                schema = Schema(implementation = UserPreferencesResponse::class),
              )
            ],
        )
      ],
  )
  suspend fun patch(
    @Valid @RequestBody request: PatchUserPreferencesRequest,
    principal: AuthenticatedPrincipal,
  ): UserPreferencesResponse =
    UserPreferencesResponse.from(service.updateLocale(principal, request.locale))
}

@Schema(description = "Partial update to preferences owned by the authenticated user.")
data class PatchUserPreferencesRequest(
  @field:Schema(
    description = "BCP 47 locale override, or null to follow the active tenant.",
    example = "en-US",
    types = ["string", "null"],
  )
  val locale: String?
)

@Schema(description = "Preferences owned by the authenticated user.")
data class UserPreferencesResponse(
  @field:Schema(
    description = "BCP 47 locale override, or null when inherited.",
    types = ["string", "null"],
  )
  val locale: String?
) {
  companion object {
    fun from(view: UserPreferencesView): UserPreferencesResponse =
      UserPreferencesResponse(locale = view.locale)
  }
}
