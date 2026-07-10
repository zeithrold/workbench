package ink.doa.workbench.web.api

import ink.doa.workbench.core.common.context.InstanceRequestContext
import ink.doa.workbench.core.common.context.ProjectRequestContext
import ink.doa.workbench.core.common.context.TenantRequestContext
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.springframework.aop.support.AopUtils
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Component
class WebEndpointArchitectureStartupValidator(private val applicationContext: ApplicationContext) :
  ApplicationRunner {
  override fun run(args: ApplicationArguments) {
    val controllerClasses =
      applicationContext
        .getBeansWithAnnotation(RestController::class.java)
        .values
        .map { AopUtils.getTargetClass(it) }
        .filter { it.name.startsWith("ink.doa.workbench.web.") }
    WebEndpointArchitectureRules.validateControllers(controllerClasses)
  }
}

object WebEndpointArchitectureRules {
  private val handlerMappingAnnotations =
    listOf(
      GetMapping::class.java,
      PostMapping::class.java,
      PutMapping::class.java,
      PatchMapping::class.java,
      DeleteMapping::class.java,
      RequestMapping::class.java,
    )

  fun validateControllers(classes: Collection<Class<*>>) {
    val violations = classes.flatMap(::validateControllerClass)
    if (violations.isNotEmpty()) {
      error(violations.joinToString("\n"))
    }
  }

  fun validateControllerClass(clazz: Class<*>): List<String> {
    val classTenantScoped = clazz.isAnnotationPresent(TenantScoped::class.java)
    val classProjectScoped = clazz.isAnnotationPresent(ProjectScoped::class.java)
    val classInstanceScoped = clazz.isAnnotationPresent(InstanceScoped::class.java)
    val classPublicEndpoint = clazz.isAnnotationPresent(PublicEndpoint::class.java)
    val classAuthenticatedOnly = clazz.isAnnotationPresent(AuthenticatedOnly::class.java)
    val classAuthorize =
      clazz.isAnnotationPresent(Authorize::class.java) ||
        clazz.isAnnotationPresent(AuthorizeAll::class.java)

    return clazz.declaredMethods.filter(::isHandlerMethod).flatMap { method ->
      buildList {
        addAll(
          validateScopeContext(
            clazz = clazz,
            method = method,
            classTenantScoped = classTenantScoped,
            classProjectScoped = classProjectScoped,
            classInstanceScoped = classInstanceScoped,
          )
        )
        addAll(
          validateSecuritySemantics(
            clazz = clazz,
            method = method,
            classPublicEndpoint = classPublicEndpoint,
            classAuthenticatedOnly = classAuthenticatedOnly,
            classAuthorize = classAuthorize,
          )
        )
      }
    }
  }

  private fun validateScopeContext(
    clazz: Class<*>,
    method: Method,
    classTenantScoped: Boolean,
    classProjectScoped: Boolean,
    classInstanceScoped: Boolean,
  ): List<String> {
    val methodTenantScoped =
      method.isAnnotationPresent(TenantScoped::class.java) || classTenantScoped
    val methodProjectScoped =
      method.isAnnotationPresent(ProjectScoped::class.java) || classProjectScoped
    val methodInstanceScoped =
      method.isAnnotationPresent(InstanceScoped::class.java) || classInstanceScoped

    val violations = mutableListOf<String>()
    val qualifiedName = "${clazz.name}.${method.name}"

    if (methodInstanceScoped) {
      val hasInstanceContext =
        method.parameterTypes.any { it == InstanceRequestContext::class.java }
      if (!hasInstanceContext) {
        violations +=
          "Instance-scoped controller method must accept InstanceRequestContext: $qualifiedName"
      }
    }

    if (methodProjectScoped) {
      val hasProjectContext = method.parameterTypes.any { it == ProjectRequestContext::class.java }
      if (!hasProjectContext) {
        violations +=
          "Project-scoped controller method must accept ProjectRequestContext: $qualifiedName"
      }
    }

    if (methodTenantScoped && !methodProjectScoped) {
      val hasTenantContext = method.parameterTypes.any { it == TenantRequestContext::class.java }
      if (!hasTenantContext) {
        violations +=
          "Tenant-scoped controller method must accept TenantRequestContext: $qualifiedName"
      }
    }

    return violations
  }

  private fun validateSecuritySemantics(
    clazz: Class<*>,
    method: Method,
    classPublicEndpoint: Boolean,
    classAuthenticatedOnly: Boolean,
    classAuthorize: Boolean,
  ): List<String> {
    val securityKinds =
      listOfNotNull(
        "PublicEndpoint"
          .takeIf {
            method.isAnnotationPresent(PublicEndpoint::class.java) || classPublicEndpoint
          },
        "AuthenticatedOnly"
          .takeIf {
            method.isAnnotationPresent(AuthenticatedOnly::class.java) || classAuthenticatedOnly
          },
        "Authorize"
          .takeIf {
            method.isAnnotationPresent(Authorize::class.java) ||
              method.isAnnotationPresent(AuthorizeAll::class.java) ||
              classAuthorize
          },
      )

    val qualifiedName = "${clazz.name}.${method.name}"
    return when {
      securityKinds.isEmpty() ->
        listOf(
          "Controller method must declare exactly one security semantic " +
            "(@PublicEndpoint, @AuthenticatedOnly, or @Authorize): $qualifiedName"
        )
      securityKinds.size > 1 ->
        listOf(
          "Controller method must declare exactly one security semantic " +
            "(found ${securityKinds.joinToString(", ")}): $qualifiedName"
        )
      else -> emptyList()
    }
  }

  private fun isHandlerMethod(method: Method): Boolean {
    if (method.isSynthetic || method.isBridge) return false
    if (!Modifier.isPublic(method.modifiers)) return false
    return handlerMappingAnnotations.any { method.isAnnotationPresent(it) }
  }
}
