@file:Suppress("RedundantSuspendModifier")

package ink.doa.workbench.web.api

import ink.doa.workbench.core.common.context.InstanceRequestContext
import ink.doa.workbench.core.common.context.ProjectRequestContext
import ink.doa.workbench.core.common.context.TenantRequestContext
import ink.doa.workbench.web.admin.AdminUserController
import ink.doa.workbench.web.identity.BearerTokenAuthController
import ink.doa.workbench.web.identity.FederatedAuthController
import ink.doa.workbench.web.identity.LoginDiscoveryController
import ink.doa.workbench.web.identity.MagicLinkAuthController
import ink.doa.workbench.web.identity.SessionAuthController
import ink.doa.workbench.web.identity.SessionController
import ink.doa.workbench.web.instance.InstanceSetupController
import ink.doa.workbench.web.instance.TenantAdminController
import ink.doa.workbench.web.invitation.InvitationController
import ink.doa.workbench.web.manage.ManagePermissionBindingController
import ink.doa.workbench.web.manage.ManagePermissionGroupController
import ink.doa.workbench.web.manage.ManagePermissionPolicyController
import ink.doa.workbench.web.project.ProjectController
import ink.doa.workbench.web.project.ProjectMemberController
import ink.doa.workbench.web.workitem.ProjectWorkItemCommentController
import ink.doa.workbench.web.workitem.ProjectWorkItemConfigurationController
import ink.doa.workbench.web.workitem.ProjectWorkItemController
import ink.doa.workbench.web.workitem.WorkItemIssueTypeController
import ink.doa.workbench.web.workitem.WorkItemPropertyController
import ink.doa.workbench.web.workitem.WorkItemStatusController
import ink.doa.workbench.web.workitem.WorkItemTypeConfigController
import ink.doa.workbench.web.workitem.WorkflowController
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping

class WebEndpointArchitectureStartupValidatorTest :
  StringSpec({
    "tenant scoped handler with tenant context passes" {
      WebEndpointArchitectureRules.validateControllerClass(ValidTenantController::class.java)
        .shouldBeEmpty()
    }

    "project scoped handler with project context passes" {
      WebEndpointArchitectureRules.validateControllerClass(ValidProjectController::class.java)
        .shouldBeEmpty()
    }

    "instance scoped handler with instance context passes" {
      WebEndpointArchitectureRules.validateControllerClass(ValidInstanceController::class.java)
        .shouldBeEmpty()
    }

    "public endpoint passes without authorize" {
      WebEndpointArchitectureRules.validateControllerClass(ValidPublicController::class.java)
        .shouldBeEmpty()
    }

    "authenticated only endpoint passes without authorize" {
      WebEndpointArchitectureRules.validateControllerClass(
          ValidAuthenticatedOnlyController::class.java
        )
        .shouldBeEmpty()
    }

    "authorize endpoint passes" {
      WebEndpointArchitectureRules.validateControllerClass(ValidAuthorizedController::class.java)
        .shouldBeEmpty()
    }

    "tenant scoped handler without tenant context fails" {
      val violations =
        WebEndpointArchitectureRules.validateControllerClass(
          MissingTenantContextController::class.java
        )

      violations.single() shouldContain MissingTenantContextController::class.java.name
      violations.single() shouldContain "TenantRequestContext"
    }

    "project scoped handler without project context fails" {
      val violations =
        WebEndpointArchitectureRules.validateControllerClass(
          MissingProjectContextController::class.java
        )

      violations.single() shouldContain MissingProjectContextController::class.java.name
      violations.single() shouldContain "ProjectRequestContext"
    }

    "instance scoped handler without instance context fails" {
      val violations =
        WebEndpointArchitectureRules.validateControllerClass(
          MissingInstanceContextController::class.java
        )

      violations.single() shouldContain MissingInstanceContextController::class.java.name
      violations.single() shouldContain "InstanceRequestContext"
    }

    "handler without security semantic fails" {
      val violations =
        WebEndpointArchitectureRules.validateControllerClass(MissingSecurityController::class.java)

      violations.single() shouldContain "security semantic"
      violations.single() shouldContain MissingSecurityController::class.java.name
    }

    "handler with multiple security semantics fails" {
      val violations =
        WebEndpointArchitectureRules.validateControllerClass(MultipleSecurityController::class.java)

      violations.single() shouldContain "exactly one security semantic"
      violations.single() shouldContain "PublicEndpoint"
      violations.single() shouldContain "Authorize"
    }

    "validateControllers aggregates violations" {
      shouldThrow<IllegalStateException> {
        WebEndpointArchitectureRules.validateControllers(
          listOf(ValidPublicController::class.java, MissingSecurityController::class.java)
        )
      }
    }

    "non handler methods are ignored" {
      WebEndpointArchitectureRules.validateControllerClass(
          IgnoredNonHandlerMethodController::class.java
        )
        .shouldBeEmpty()
    }

    "production controllers satisfy architecture rules" {
      val violations =
        productionControllerClasses.flatMap(WebEndpointArchitectureRules::validateControllerClass)
      violations.isEmpty() shouldBe true
    }
  })

private val productionControllerClasses =
  listOf(
    AdminUserController::class.java,
    BearerTokenAuthController::class.java,
    FederatedAuthController::class.java,
    InstanceSetupController::class.java,
    InvitationController::class.java,
    LoginDiscoveryController::class.java,
    MagicLinkAuthController::class.java,
    ManagePermissionBindingController::class.java,
    ManagePermissionGroupController::class.java,
    ManagePermissionPolicyController::class.java,
    ProjectController::class.java,
    ProjectMemberController::class.java,
    ProjectWorkItemCommentController::class.java,
    ProjectWorkItemConfigurationController::class.java,
    ProjectWorkItemController::class.java,
    SessionAuthController::class.java,
    SessionController::class.java,
    TenantAdminController::class.java,
    WorkItemIssueTypeController::class.java,
    WorkItemPropertyController::class.java,
    WorkItemStatusController::class.java,
    WorkItemTypeConfigController::class.java,
    WorkflowController::class.java,
  )

private class ValidTenantController {
  @GetMapping("/tenant")
  @TenantScoped
  @Authorize(action = "project.read", resource = "project")
  suspend fun list(tenantContext: TenantRequestContext): String =
    tenantContext.tenant.publicId.value
}

private class ValidProjectController {
  @GetMapping("/project")
  @TenantScoped
  @ProjectScoped
  @Authorize(action = "project.read", resource = "project")
  suspend fun get(projectContext: ProjectRequestContext): String =
    projectContext.project.publicId.value
}

private class ValidInstanceController {
  @GetMapping("/instance")
  @InstanceScoped
  @Authorize(action = "tenant.read", resource = "tenant")
  suspend fun list(instanceContext: InstanceRequestContext): String = instanceContext.instance.id
}

private class ValidPublicController {
  @GetMapping("/public") @PublicEndpoint suspend fun ping(): String = javaClass.simpleName
}

@AuthenticatedOnly
private class ValidAuthenticatedOnlyController {
  @GetMapping("/session") suspend fun session(): String = javaClass.simpleName
}

private class ValidAuthorizedController {
  @GetMapping("/authorized")
  @Authorize(action = "project.read", resource = "project")
  suspend fun list(): String = javaClass.simpleName
}

private class MissingTenantContextController {
  @GetMapping("/tenant")
  @TenantScoped
  @Authorize(action = "project.read", resource = "project")
  suspend fun list(): String = javaClass.simpleName
}

private class MissingProjectContextController {
  @GetMapping("/project")
  @ProjectScoped
  @Authorize(action = "project.read", resource = "project")
  suspend fun get(): String = javaClass.simpleName
}

private class MissingInstanceContextController {
  @GetMapping("/instance")
  @InstanceScoped
  @Authorize(action = "tenant.read", resource = "tenant")
  suspend fun list(): String = javaClass.simpleName
}

private class MissingSecurityController {
  @GetMapping("/missing-security") suspend fun ping(): String = javaClass.simpleName
}

private class MultipleSecurityController {
  @GetMapping("/multiple")
  @PublicEndpoint
  @Authorize(action = "project.read", resource = "project")
  suspend fun ping(): String = javaClass.simpleName
}

private class IgnoredNonHandlerMethodController {
  @PublicEndpoint @PostMapping("/public") suspend fun create(): String = javaClass.simpleName
}
