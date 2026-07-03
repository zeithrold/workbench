package ink.doa.workbench.web.api

import ink.doa.workbench.agile.project.ProjectOperationalGuard
import ink.doa.workbench.agile.project.ProjectResolver
import ink.doa.workbench.core.common.context.ProjectContextSummary
import ink.doa.workbench.core.common.context.ProjectRequestContext
import ink.doa.workbench.core.common.context.TenantRequestContext
import ink.doa.workbench.core.common.errors.InvalidRequestException
import ink.doa.workbench.core.common.errors.WorkbenchErrorCode
import kotlinx.coroutines.runBlocking
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.servlet.HandlerMapping

@Component
class ProjectRequestContextResolver(
  private val tenantRequestContextResolver: TenantRequestContextResolver,
  private val projectResolver: ProjectResolver,
  private val projectOperationalGuard: ProjectOperationalGuard,
) : HandlerMethodArgumentResolver {
  override fun supportsParameter(parameter: MethodParameter): Boolean =
    parameter.parameterType == ProjectRequestContext::class.java

  override fun resolveArgument(
    parameter: MethodParameter,
    mavContainer: ModelAndViewContainer?,
    webRequest: NativeWebRequest,
    binderFactory: WebDataBinderFactory?,
  ): Any {
    val tenantContext =
      tenantRequestContextResolver.resolveArgument(
        parameter,
        mavContainer,
        webRequest,
        binderFactory,
      ) as TenantRequestContext
    val projectPublicId = requireProjectPublicId(webRequest)
    val project = runBlocking {
      val resolved = projectResolver.resolveProject(tenantContext.tenant.id, projectPublicId)
      projectOperationalGuard.ensureOperational(tenantContext.tenant.id, resolved.id)
      resolved
    }
    return ProjectRequestContext(
      requestId = tenantContext.requestId,
      apiVersion = tenantContext.apiVersion,
      actor = tenantContext.actor,
      receivedAt = tenantContext.receivedAt,
      instance = tenantContext.instance,
      tenant = tenantContext.tenant,
      project = ProjectContextSummary.from(project),
    )
  }

  private fun requireProjectPublicId(webRequest: NativeWebRequest): String {
    @Suppress("UNCHECKED_CAST")
    val uriVariables =
      webRequest.getAttribute(
        HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
        NativeWebRequest.SCOPE_REQUEST,
      ) as? Map<String, String>
    return uriVariables?.get("id")
      ?: throw InvalidRequestException(WorkbenchErrorCode.REQUEST_PROJECT_ID_REQUIRED)
  }
}
