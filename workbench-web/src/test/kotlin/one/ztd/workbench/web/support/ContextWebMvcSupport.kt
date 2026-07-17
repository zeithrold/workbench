package one.ztd.workbench.web.support

import io.mockk.mockk
import one.ztd.workbench.agile.project.ProjectResolver
import one.ztd.workbench.tenant.instance.InstanceContextProvider
import one.ztd.workbench.tenant.instance.InstanceProperties
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class ContextWebMvcSupport {
  @Bean
  fun instanceContextProvider(): InstanceContextProvider =
    InstanceContextProvider(
      instanceProperties =
        InstanceProperties(setupToken = null, id = "test-instance", name = "workbench"),
      applicationName = "workbench",
    )

  @Bean fun projectResolver(): ProjectResolver = mockk(relaxed = true)
}
