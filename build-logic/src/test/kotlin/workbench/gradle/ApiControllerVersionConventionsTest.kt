package workbench.gradle

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class ApiControllerVersionConventionsTest {
    @Test
    fun `accepts one stable current controller and a dated deprecated legacy controller`() {
        val root = Files.createTempDirectory("api-controller-version")
        root.write(
            "workbench-web/src/main/kotlin/one/ztd/workbench/web/project/ProjectController.kt",
            """
            package one.ztd.workbench.web.project

            @RestController
            class ProjectController
            """.trimIndent(),
        )
        root.write(
            "workbench-web/src/main/kotlin/one/ztd/workbench/web/project/legacy/ProjectV20260703Controller.kt",
            """
            package one.ztd.workbench.web.project.legacy

            @Deprecated("Compatible with clients pinned to 2026-07-03")
            @RestController
            class ProjectV20260703Controller
            """.trimIndent(),
        )

        val violations = inspect(root)

        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }

    @Test
    fun `rejects mixed controllers`() {
        val root = Files.createTempDirectory("api-controller-version")
        root.write(
            "workbench-web/src/main/kotlin/one/ztd/workbench/web/project/WrongController.kt",
            """
            package one.ztd.workbench.web.project

            @RestController
            class ProjectController

            @RestController
            class ProjectAdminController
            """.trimIndent(),
        )

        val violations = inspect(root).joinToString("\n")

        assertContains(violations, "exactly one @RestController")
        assertContains(violations, "exactly one class ending in Controller")
    }

    @Test
    fun `rejects mismatched controller file and class names`() {
        val root = Files.createTempDirectory("api-controller-version")
        root.write(
            "workbench-web/src/main/kotlin/one/ztd/workbench/web/project/WrongController.kt",
            """
            package one.ztd.workbench.web.project

            @RestController
            class ProjectController
            """.trimIndent(),
        )

        val violations = inspect(root).joinToString("\n")

        assertContains(violations, "file name must match class name")
    }

    @Test
    fun `rejects versioned controller outside legacy package`() {
        val root = Files.createTempDirectory("api-controller-version")
        root.write(
            "workbench-web/src/main/kotlin/one/ztd/workbench/web/project/ProjectV20260703Controller.kt",
            """
            package one.ztd.workbench.web.project

            @RestController
            class ProjectV20260703Controller
            """.trimIndent(),
        )

        val violations = inspect(root).joinToString("\n")

        assertContains(violations, "must be in a legacy package")
    }

    @Test
    fun `rejects deprecated current controller`() {
        val root = Files.createTempDirectory("api-controller-version")
        root.write(
            "workbench-web/src/main/kotlin/one/ztd/workbench/web/project/ProjectController.kt",
            """
            package one.ztd.workbench.web.project

            @Deprecated("Old contract")
            @RestController
            class ProjectController
            """.trimIndent(),
        )

        val violations = inspect(root).joinToString("\n")

        assertContains(violations, "current controller ProjectController must not be annotated")
    }

    @Test
    fun `rejects invalid legacy naming and missing deprecation marker`() {
        val root = Files.createTempDirectory("api-controller-version")
        root.write(
            "workbench-web/src/main/kotlin/one/ztd/workbench/web/project/legacy/ProjectOldController.kt",
            """
            package one.ztd.workbench.web.project.legacy

            @RestController
            class ProjectOldController
            """.trimIndent(),
        )

        val violations = inspect(root).joinToString("\n")

        assertContains(violations, "must use {Resource}VyyyyMMddController naming")
        assertContains(violations, "must be annotated @Deprecated")
    }

    @Test
    fun `rejects legacy controller without current counterpart`() {
        val root = Files.createTempDirectory("api-controller-version")
        root.write(
            "workbench-web/src/main/kotlin/one/ztd/workbench/web/project/legacy/ProjectV20260703Controller.kt",
            """
            package one.ztd.workbench.web.project.legacy

            @Deprecated("Compatible with clients pinned to 2026-07-03")
            @RestController
            class ProjectV20260703Controller
            """.trimIndent(),
        )

        val violations = inspect(root).joinToString("\n")

        assertContains(
            violations,
            "must have current counterpart one.ztd.workbench.web.project.ProjectController",
        )
    }

    private fun inspect(root: java.nio.file.Path): List<String> =
        ApiControllerVersionConventions.inspect(
            root.toFile(),
            root.resolve("workbench-web/src/main/kotlin").toFile().walkTopDown().toList(),
        )

    private fun java.nio.file.Path.write(relativePath: String, content: String) {
        val target = resolve(relativePath)
        target.parent.createDirectories()
        target.writeText(content)
    }
}
