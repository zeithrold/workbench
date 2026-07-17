package workbench.gradle

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class ModuleArchitectureConventionsTest {
    @Test
    fun `accepts the target dependency graph and scoped packages`() {
        val root = Files.createTempDirectory("module-architecture")
        root.write("workbench-kernel/build.gradle.kts", "plugins {}")
        root.write(
            "workbench-kernel/src/main/kotlin/one/ztd/workbench/kernel/Ids.kt",
            "package one.ztd.workbench.kernel.common.ids",
        )
        root.write(
            "workbench-tenant/build.gradle.kts",
            "implementation(project(\":workbench-kernel\"))",
        )
        root.write(
            "workbench-tenant/src/main/kotlin/one/ztd/workbench/tenant/Tenant.kt",
            "package one.ztd.workbench.tenant",
        )

        val violations = ModuleArchitectureConventions.inspect(root.toFile())
        assertTrue(violations.isEmpty(), violations.joinToString("\n"))
    }

    @Test
    fun `rejects illegal dependencies package roots technology leaks and global scanning`() {
        val root = Files.createTempDirectory("module-architecture")
        root.write(
            "workbench-agile/build.gradle.kts",
            "implementation(project(\":workbench-data\"))",
        )
        root.write(
            "workbench-agile/src/main/kotlin/example/Leak.kt",
            """
            package example
            import org.jetbrains.exposed.sql.Table
            """.trimIndent(),
        )
        root.write(
            "workbench-web/src/main/kotlin/one/ztd/workbench/web/App.kt",
            """
            package one.ztd.workbench.web
            @SpringBootApplication(scanBasePackages = ["one.ztd.workbench"])
            class App
            """.trimIndent(),
        )

        val violations = ModuleArchitectureConventions.inspect(root.toFile()).joinToString("\n")
        assertContains(violations, "production dependency on workbench-data is not allowed")
        assertContains(violations, "package must be rooted")
        assertContains(violations, "domain module must not reference org.jetbrains.exposed")
        assertContains(violations, "full repository component scanning is forbidden")
    }

    private fun java.nio.file.Path.write(relativePath: String, content: String) {
        val target = resolve(relativePath)
        target.parent.createDirectories()
        target.writeText(content)
    }
}
