package ink.doa.workbench.web

import ink.doa.workbench.application.ApplicationModuleConfiguration
import ink.doa.workbench.data.DataModuleConfiguration
import ink.doa.workbench.security.SecurityModuleConfiguration
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.springframework.context.annotation.Import

class WebCompositionTest :
  StringSpec({
    "web imports application data and security library configurations" {
      val imports =
        WorkbenchApplication::class.java.getAnnotation(Import::class.java).value.toList()

      imports shouldContainExactlyInAnyOrder
        listOf(
          ApplicationModuleConfiguration::class,
          DataModuleConfiguration::class,
          SecurityModuleConfiguration::class,
        )
    }
  })
