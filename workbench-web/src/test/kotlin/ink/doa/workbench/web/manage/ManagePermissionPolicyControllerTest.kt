package ink.doa.workbench.web.manage

import ink.doa.workbench.application.permission.PermissionPolicyManagementService
import ink.doa.workbench.identity.SessionService
import ink.doa.workbench.identity.permission.PermissionPolicyRecord
import ink.doa.workbench.identity.permission.PermissionPolicyRepository
import ink.doa.workbench.kernel.common.ids.PublicId
import ink.doa.workbench.security.SecurityConfiguration
import ink.doa.workbench.security.WORKBENCH_SESSION_COOKIE_NAME
import ink.doa.workbench.security.WorkbenchAuthenticationFilter
import ink.doa.workbench.web.api.GlobalExceptionHandler
import ink.doa.workbench.web.api.InfrastructureAspect
import ink.doa.workbench.web.api.RequestContextResolver
import ink.doa.workbench.web.api.TenantRequestContextResolver
import ink.doa.workbench.web.support.TenantScopedWebMvcSupport
import ink.doa.workbench.web.support.TenantWebMvcFixtures
import io.mockk.coEvery
import io.mockk.mockk
import jakarta.servlet.http.Cookie
import java.time.Clock
import java.time.OffsetDateTime
import java.util.UUID
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

@WebMvcTest(ManagePermissionPolicyController::class)
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
  ManagePermissionPolicyControllerTest.TestBeans::class,
)
class ManagePermissionPolicyControllerTest(@Autowired private val mockMvc: MockMvc) {
  @Test
  fun `list permission policies rejects unauthenticated requests`() {
    mockMvc.perform(get("/api/manage/permission-policies")).andExpect(status().isUnauthorized())
  }

  @Test
  fun `list permission policies returns policies for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          get("/api/manage/permission-policies")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$[0].code").value("admin"))
  }

  @Test
  fun `get permission policy returns a complete document`() {
    val result =
      mockMvc
        .perform(
          get("/api/manage/permission-policies/pol_01JABCDEFGHJKMNPQRSTVWXYZ0")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.code").value("admin"))
      .andExpect(jsonPath("$.revision").value("2026-07-04T00:00Z"))
  }

  @Test
  fun `create permission policy returns created policy for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          post("/api/manage/permission-policies")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "code": "editor",
                "name": "Editor",
                "description": "Can manage members",
                "rules": [
                  {
                    "action": "tenant.member.manage",
                    "resourcePattern": "tenant:*",
                    "effect": "allow"
                  }
                ]
              }
              """
                .trimIndent()
            )
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.code").value("editor"))
  }

  @Test
  fun `replace permission policy accepts a complete policy document`() {
    val result =
      mockMvc
        .perform(
          put("/api/manage/permission-policies/pol_01JABCDEFGHJKMNPQRSTVWXYZ0")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "schemaVersion": 1,
                "revision": "2026-07-04T00:00Z",
                "code": "admin",
                "name": "Tenant admin",
                "description": "Complete tenant access",
                "rules": [
                  {
                    "id": null,
                    "action": "tenant.update",
                    "resourcePattern": "tenant:*",
                    "effect": "deny",
                    "condition": null
                  }
                ]
              }
              """
                .trimIndent()
            )
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.name").value("Tenant admin"))
      .andExpect(jsonPath("$.rules[0].action").value("tenant.update"))
  }

  @Test
  fun `add policy rule returns updated policy for authenticated tenant user`() {
    val result =
      mockMvc
        .perform(
          post("/api/manage/permission-policies/pol_01JABCDEFGHJKMNPQRSTVWXYZ0/rules")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "action": "tenant.update",
                "resourcePattern": "tenant:*"
              }
              """
                .trimIndent()
            )
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isCreated())
      .andExpect(jsonPath("$.rules[0].action").value("tenant.update"))
  }

  @Test
  fun `add policy rule rejects conditions`() {
    val result =
      mockMvc
        .perform(
          post("/api/manage/permission-policies/pol_01JABCDEFGHJKMNPQRSTVWXYZ0/rules")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "action": "tenant.read",
                "resourcePattern": "tenant:*",
                "condition": {"field":"tenant.slug","op":"eq","value":"demo"}
              }
              """
                .trimIndent()
            )
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.code").value("permission.policy.tenant_condition_forbidden"))
  }

  @Test
  fun `add policy rule rejects Agile action with tenant boundary error code`() {
    val result =
      mockMvc
        .perform(
          post("/api/manage/permission-policies/pol_01JABCDEFGHJKMNPQRSTVWXYZ0/rules")
            .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
            .contentType(MediaType.APPLICATION_JSON)
            .content(
              """
              {
                "action": "issue.view",
                "resourcePattern": "issue:*",
                "condition": null
              }
              """
                .trimIndent()
            )
        )
        .andExpect(request().asyncStarted())
        .andReturn()

    mockMvc
      .perform(asyncDispatch(result))
      .andExpect(status().isBadRequest)
      .andExpect(jsonPath("$.code").value("permission.policy.tenant_action_forbidden"))
  }

  @Test
  fun `simulate permission policy applies tenant deny precedence`() {
    mockMvc
      .perform(
        post("/api/manage/permission-policies/simulate")
          .cookie(Cookie(WORKBENCH_SESSION_COOKIE_NAME, TenantWebMvcFixtures.SESSION))
          .contentType(MediaType.APPLICATION_JSON)
          .content(
            """
            {
              "schemaVersion": 1,
              "action": "tenant.read",
              "rules": [
                {"action":"tenant.read","resourcePattern":"tenant:*","effect":"allow"},
                {"action":"tenant.read","resourcePattern":"tenant:*","effect":"deny"}
              ]
            }
            """
              .trimIndent()
          )
      )
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.decision").value("DENY"))
      .andExpect(jsonPath("$.reason").value("matching_deny"))
  }

  @TestConfiguration
  class TestBeans {
    @Bean
    fun sessionAuthenticator(): ink.doa.workbench.identity.auth.SessionAuthenticator =
      object : ink.doa.workbench.identity.auth.SessionAuthenticator {
        override suspend fun authenticateSession(sessionId: String) =
          if (sessionId == TenantWebMvcFixtures.SESSION) TenantWebMvcFixtures.PRINCIPAL else null
      }

    @Bean
    fun bearerTokenAuthenticator(): ink.doa.workbench.identity.auth.BearerTokenAuthenticator =
      object : ink.doa.workbench.identity.auth.BearerTokenAuthenticator {
        override suspend fun authenticateBearerToken(token: String) = null
      }

    @Bean
    fun sessionService(): SessionService = mockk {
      coEvery { requireActiveTenant(any()) } returns TenantWebMvcFixtures.TENANT_RECORD
    }

    @Bean fun permissionPolicyRepository(): PermissionPolicyRepository = mockk()

    @Bean
    fun permissionPolicyManagementService(
      policies: PermissionPolicyRepository,
      clock: Clock,
    ): PermissionPolicyManagementService = PermissionPolicyManagementService(policies, clock)

    @Bean
    fun permissionPolicyRepositorySetup(policies: PermissionPolicyRepository): Boolean {
      val tenantId = TenantWebMvcFixtures.TENANT_ID
      val now = OffsetDateTime.parse("2026-07-04T00:00:00Z")
      val customPolicyId = UUID.randomUUID()
      val adminPolicy =
        PermissionPolicyRecord(
          id = customPolicyId,
          apiId = PublicId("pol_01JABCDEFGHJKMNPQRSTVWXYZ0"),
          tenantId = tenantId,
          code = "admin",
          name = "Admin",
          description = "Full access",
          builtin = false,
          createdAt = now,
          updatedAt = now,
        )
      val editorPolicy =
        PermissionPolicyRecord(
          id = UUID.randomUUID(),
          apiId = PublicId("pol_01JABCDEFGHJKMNPQRSTVWXYZ1"),
          tenantId = tenantId,
          code = "editor",
          name = "Editor",
          description = "Can manage members",
          builtin = false,
          createdAt = now,
          updatedAt = now,
        )
      val tenantReadRule =
        ink.doa.workbench.identity.permission.PermissionPolicyRuleRecord(
          id = UUID.randomUUID(),
          apiId = PublicId.new("prr"),
          policyId = customPolicyId,
          action = ink.doa.workbench.identity.permission.model.AuthorizationAction("tenant.read"),
          resourcePattern = "tenant:*",
          effect = ink.doa.workbench.identity.permission.model.PermissionEffect.ALLOW,
          conditionJson = null,
          createdAt = now,
        )
      val tenantUpdateRule =
        tenantReadRule.copy(
          id = UUID.randomUUID(),
          apiId = PublicId.new("prr"),
          action = ink.doa.workbench.identity.permission.model.AuthorizationAction("tenant.update"),
          effect = ink.doa.workbench.identity.permission.model.PermissionEffect.DENY,
        )
      var rules: List<ink.doa.workbench.identity.permission.PermissionPolicyRuleRecord> =
        listOf(tenantReadRule)

      coEvery { policies.list(tenantId) } returns listOf(adminPolicy)
      coEvery { policies.findByCode(tenantId, "editor") } returns null
      coEvery { policies.create(any()) } returns editorPolicy
      coEvery { policies.listRules(editorPolicy.id) } returns
        listOf(tenantReadRule.copy(policyId = editorPolicy.id))
      coEvery { policies.findByApiId(tenantId, "pol_01JABCDEFGHJKMNPQRSTVWXYZ0") } returns
        adminPolicy
      coEvery { policies.replace(any()) } answers
        {
          rules = listOf(tenantUpdateRule)
          adminPolicy.copy(
            name = "Tenant admin",
            description = "Complete tenant access",
            updatedAt = now.plusSeconds(1),
          )
        }
      coEvery { policies.addRule(any()) } answers
        {
          val command =
            firstArg<ink.doa.workbench.identity.permission.CreatePermissionPolicyRuleCommand>()
          val rule =
            when (command.action.code) {
              "tenant.update" -> tenantUpdateRule
              else -> error("unexpected action ${command.action.code}")
            }
          rules = listOf(rule)
          rule
        }
      coEvery { policies.listRules(customPolicyId) } answers { rules }
      return true
    }
  }
}
