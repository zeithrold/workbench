package ink.doa.workbench.worker

import ink.doa.workbench.application.WorkerApplicationModuleConfiguration
import ink.doa.workbench.data.DataModuleConfiguration
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.springframework.context.annotation.Import

class WorkerCompositionTest :
  StringSpec({
    "worker imports only worker application and data library configurations" {
      val imports =
        WorkbenchWorkerApplication::class.java.getAnnotation(Import::class.java).value.toList()

      imports shouldContainExactlyInAnyOrder
        listOf(WorkerApplicationModuleConfiguration::class, DataModuleConfiguration::class)
    }

    "worker runtime classpath excludes web controllers and Spring Security filters" {
      classIsPresent("ink.doa.workbench.web.project.ProjectController") shouldBe false
      classIsPresent("org.springframework.security.web.SecurityFilterChain") shouldBe false
    }
  })

private fun classIsPresent(name: String): Boolean = runCatching { Class.forName(name) }.isSuccess
