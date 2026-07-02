package doa.ink.workbench.api

import doa.ink.workbench.shared.context.TenantRequestContext
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
      val classScoped = targetClass.isAnnotationPresent(TenantScoped::class.java)
      targetClass.declaredMethods.forEach { method ->
        val methodScoped = method.isAnnotationPresent(TenantScoped::class.java) || classScoped
        if (methodScoped && method.parameterTypes.none { it == TenantRequestContext::class.java }) {
          error(
            "Tenant-scoped controller method must accept TenantRequestContext: ${targetClass.name}.${method.name}"
          )
        }
      }
    }
  }
}
