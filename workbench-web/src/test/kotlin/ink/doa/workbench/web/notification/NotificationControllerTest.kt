package ink.doa.workbench.web.notification

import ink.doa.workbench.core.notification.NotificationPreferenceRecord
import ink.doa.workbench.security.SecurityConfiguration
import ink.doa.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import ink.doa.workbench.security.WorkbenchAuthenticationFilter
import ink.doa.workbench.security.identity.SessionService
import ink.doa.workbench.service.notification.NotificationApplicationService
import ink.doa.workbench.service.notification.NotificationView
import ink.doa.workbench.web.api.GlobalExceptionHandler
import ink.doa.workbench.web.api.InfrastructureAspect
import ink.doa.workbench.web.api.RequestContextResolver
import ink.doa.workbench.web.api.TenantRequestContextResolver
import ink.doa.workbench.web.support.TenantScopedWebMvcSupport
import ink.doa.workbench.web.support.TenantWebMvcFixtures
import io.mockk.coEvery
import io.mockk.mockk
import jakarta.servlet.http.Cookie
import java.time.OffsetDateTime
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(NotificationController::class)
@Import(
  SecurityConfiguration::class,
  WorkbenchAuthenticationFilter::class,
  AopAutoConfiguration::class,
  InfrastructureAspect::class,
  RequestContextResolver::class,
  TenantRequestContextResolver::class,
  ink.doa.workbench.web.support.ContextWebMvcSupport::class,
  ink.doa.workbench.web.support.ProjectWebMvcSupport::class,
  TenantScopedWebMvcSupport::class,
  GlobalExceptionHandler::class,
  NotificationControllerTest.TestBeans::class,
)
class NotificationControllerTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `list notifications returns mapped responses`() {
    val result =
      mockMvc
        .perform(
          get("/api/notifications")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].id").value(NOTIFICATION_VIEW.id))
      .andExpect(jsonPath("$[0].title").value("Assigned"))
  }

  @Test
  fun `unread count returns count`() {
    val result =
      mockMvc
        .perform(
          get("/api/notifications/unread-count")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.count").value(2))
  }

  @Test
  fun `mark read and mark all read return no content`() {
    val markRead =
      mockMvc
        .perform(
          post("/api/notifications/ntf_01JABCDEFGHJKMNPQRSTVWXYZ0/read")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc.perform(asyncDispatch(markRead)).andExpect(status().isNoContent())

    val markAll =
      mockMvc
        .perform(
          post("/api/notifications/read-all")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc.perform(asyncDispatch(markAll)).andExpect(status().isNoContent())
  }

  @Test
  fun `preferences endpoints list and update preferences`() {
    val list =
      mockMvc
        .perform(
          get("/api/notifications/preferences")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(list))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].notificationType").value("work_item.assigned"))

    val update =
      mockMvc
        .perform(
          put("/api/notifications/preferences")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "notificationType": "work_item.assigned",
                "inAppEnabled": false,
                "emailEnabled": true
              }
              """
                .trimIndent()
            )
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(update))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.inAppEnabled").value(false))
  }

  @TestConfiguration
  class TestBeans {
    @Bean
    fun sessionAuthenticator(): ink.doa.workbench.core.identity.auth.SessionAuthenticator =
      object : ink.doa.workbench.core.identity.auth.SessionAuthenticator {
        override suspend fun authenticateSession(sessionId: String) =
          if (sessionId == TenantWebMvcFixtures.SESSION) TenantWebMvcFixtures.PRINCIPAL else null
      }

    @Bean
    fun bearerTokenAuthenticator(): ink.doa.workbench.core.identity.auth.BearerTokenAuthenticator =
      object : ink.doa.workbench.core.identity.auth.BearerTokenAuthenticator {
        override suspend fun authenticateBearerToken(token: String) = null
      }

    @Bean
    fun sessionService(): SessionService = mockk {
      coEvery { requireActiveTenant(any()) } returns TenantWebMvcFixtures.TENANT_RECORD
    }

    @Bean
    fun notificationService(): NotificationApplicationService {
      val service = mockk<NotificationApplicationService>()
      coEvery {
        service.list(TenantWebMvcFixtures.USER_ID, TenantWebMvcFixtures.TENANT_ID, 50, 0)
      } returns listOf(NOTIFICATION_VIEW)
      coEvery {
        service.unreadCount(TenantWebMvcFixtures.USER_ID, TenantWebMvcFixtures.TENANT_ID)
      } returns 2
      coEvery {
        service.markRead(
          TenantWebMvcFixtures.USER_ID,
          TenantWebMvcFixtures.TENANT_ID,
          NOTIFICATION_VIEW.id,
        )
      } returns true
      coEvery {
        service.markAllRead(TenantWebMvcFixtures.USER_ID, TenantWebMvcFixtures.TENANT_ID)
      } returns 1
      coEvery { service.listPreferences(TenantWebMvcFixtures.USER_ID) } returns listOf(PREFERENCE)
      coEvery {
        service.updatePreference(
          TenantWebMvcFixtures.USER_ID,
          "work_item.assigned",
          false,
          true,
        )
      } returns PREFERENCE.copy(inAppEnabled = false)
      return service
    }
  }

  private companion object {
    val NOTIFICATION_VIEW =
      NotificationView(
        id = "ntf_01JABCDEFGHJKMNPQRSTVWXYZ0",
        recipientUserId = TenantWebMvcFixtures.USER_ID,
        tenantId = TenantWebMvcFixtures.TENANT_ID,
        projectId = TenantWebMvcFixtures.PROJECT_ID,
        workItemId = UUID.fromString("00000000-0000-0000-0000-000000000030"),
        notificationType = "work_item.assigned",
        title = "Assigned",
        body = "You were assigned",
        payload = JsonObject(emptyMap()),
        readAt = null,
        createdAt = OffsetDateTime.parse("2026-07-03T00:00:00Z"),
      )
    val PREFERENCE =
      NotificationPreferenceRecord(
        userId = TenantWebMvcFixtures.USER_ID,
        notificationType = "work_item.assigned",
        inAppEnabled = true,
        emailEnabled = true,
      )
  }
}
