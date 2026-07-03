package ink.doa.workbench.web.api

import ink.doa.workbench.core.common.context.ProjectRequestContext
import ink.doa.workbench.core.common.context.TenantRequestContext
import org.springframework.aop.support.AopUtils
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.RestController

@Component
class TenantScopedStartupValidator(private val applicationContext: ApplicationContext) :
  ApplicationRunner {
  override fun run(args: ApplicationArguments) {
    applicationContext.getBeansWithAnnotation(RestController::class.java).values.forEach { bean ->
      val targetClass = AopUtils.getTargetClass(bean)
      val classTenantScoped = targetClass.isAnnotationPresent(TenantScoped::class.java)
      val classProjectScoped = targetClass.isAnnotationPresent(ProjectScoped::class.java)
      targetClass.declaredMethods.forEach { method ->
        val methodTenantScoped =
          method.isAnnotationPresent(TenantScoped::class.java) || classTenantScoped
        if (!methodTenantScoped) return@forEach

        val methodProjectScoped =
          method.isAnnotationPresent(ProjectScoped::class.java) || classProjectScoped
        val hasTenantContext = method.parameterTypes.any { it == TenantRequestContext::class.java }
        val hasProjectContext =
          method.parameterTypes.any { it == ProjectRequestContext::class.java }
        val contextSatisfied = hasTenantContext || (methodProjectScoped && hasProjectContext)
        if (!contextSatisfied) {
          val expected =
            if (methodProjectScoped) "TenantRequestContext or ProjectRequestContext"
            else "TenantRequestContext"
          error(
            "Tenant-scoped controller method must accept $expected: ${targetClass.name}.${method.name}"
          )
        }
      }
    }
  }
}
