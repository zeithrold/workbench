package workbench.gradle

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class TestArchitectureConventionsTest {
    @Test
    fun `accepts a valid unit web slice and integration test`() {
        val root = Files.createTempDirectory("test-architecture")
        root.writeTest(
            "workbench-web/src/test/kotlin/example/ProjectControllerTest.kt",
            """
            import org.junit.jupiter.api.Test
            import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest

            @WebMvcTest(ProjectController::class)
            class ProjectControllerTest {
              @Test fun works() = Unit
            }
            """.trimIndent(),
        )
        root.writeTest(
            "workbench-data/src/test/kotlin/example/ProjectRepositoryIntegrationTest.kt",
            """
            import io.kotest.core.annotation.Tags

            @Tags("integration")
            class ProjectRepositoryIntegrationTest
            """.trimIndent(),
        )

        val violations = TestArchitectureConventions.inspect(root.toFile())
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }

    @Test
    fun `reports naming tag framework and MockMvc port violations`() {
        val root = Files.createTempDirectory("test-architecture")
        root.writeTest(
            "workbench-web/src/test/kotlin/example/ProjectControllerDirectTest.kt",
            """
            import io.kotest.core.annotation.Tags
            import io.kotest.core.spec.style.StringSpec
            import org.springframework.boot.test.context.SpringBootTest
            import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc

            @Tags("integration")
            @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
            @AutoConfigureMockMvc
            class ProjectControllerDirectTest : StringSpec()
            """.trimIndent(),
        )
        root.writeTest(
            "workbench-data/src/test/kotlin/example/ProjectRepositoryIntegrationTest.kt",
            "class ProjectRepositoryIntegrationTest",
        )

        val violations = TestArchitectureConventions.inspect(root.toFile()).joinToString("\n")

        assertContains(violations, "*IntegrationTest suffix")
        assertContains(violations, "*DirectTest is retired")
        assertContains(violations, "must use JUnit 5")
        assertContains(violations, "must import org.junit.jupiter.api.Test")
        assertContains(violations, "must declare @Tag(\"integration\")")
        assertContains(violations, "MockMvc tests must use WebEnvironment.MOCK")
        assertContains(violations, "*IntegrationTest must declare an integration tag")
    }

    private fun java.nio.file.Path.writeTest(relativePath: String, source: String) {
        val target = resolve(relativePath)
        target.parent.createDirectories()
        target.writeText(source)
    }
}
