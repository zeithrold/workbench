package ink.doa.workbench.identity

import ink.doa.workbench.identity.model.AuthenticatedPrincipal
import ink.doa.workbench.kernel.common.errors.InvalidRequestException
import ink.doa.workbench.kernel.common.errors.ResourceNotFoundException
import ink.doa.workbench.kernel.common.errors.WorkbenchErrorCode
import java.time.Clock
import java.time.OffsetDateTime
import java.util.IllformedLocaleException
import java.util.Locale
import org.springframework.stereotype.Service

@Service
class UserPreferenceService(
  private val preferences: UserPreferenceRepository,
  private val clock: Clock,
) {
  suspend fun updateLocale(
    principal: AuthenticatedPrincipal,
    locale: String?,
  ): UserPreferencesView {
    val normalized = locale?.let(::normalizeLocale)
    val updated =
      preferences.updateLocale(principal.user.id, normalized, OffsetDateTime.now(clock))
        ?: throw ResourceNotFoundException(WorkbenchErrorCode.RESOURCE_USER_NOT_FOUND)
    return UserPreferencesView(locale = updated.locale)
  }

  private fun normalizeLocale(locale: String): String =
    try {
      Locale.Builder().setLanguageTag(locale).build().toLanguageTag().also {
        if (it == "und") throw IllformedLocaleException("Locale must include a language tag.")
      }
    } catch (_: IllformedLocaleException) {
      throw InvalidRequestException(
        WorkbenchErrorCode.REQUEST_INVALID,
        "Locale must be a valid BCP 47 language tag.",
      )
    }
}
