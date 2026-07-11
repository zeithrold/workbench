package workbench.gradle

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class DetektSuppressionConventionsTest {
    @Test
    fun `accepts registered narrow suppression`() {
        val root = Files.createTempDirectory("suppression-governance")
        val source =
            root.writeSource(
                "workbench-web/src/main/kotlin/ink/doa/workbench/web/api/InfrastructureAspect.kt",
                "fun audit() = try { Unit } catch (@Suppress(\"TooGenericExceptionCaught\") error: RuntimeException) { throw error }",
            )

        assertTrue(DetektSuppressionConventions.inspect(root.toFile(), listOf(source)).isEmpty())
    }

    @Test
    fun `rejects file level unregistered and excessive suppressions`() {
        val root = Files.createTempDirectory("suppression-governance")
        val broad =
            root.writeSource(
                "workbench-core/src/main/kotlin/example/Broad.kt",
                "@file:Suppress(\"LongMethod\")\npackage example",
            )
        val excessive =
            root.writeSource(
                "workbench-web/src/main/kotlin/ink/doa/workbench/web/api/InfrastructureAspect.kt",
                "@Suppress(\"TooGenericExceptionCaught\") fun one() = Unit\n" +
                    "@Suppress(\"TooGenericExceptionCaught\") fun two() = Unit",
            )

        val violations =
            DetektSuppressionConventions.inspect(root.toFile(), listOf(broad, excessive)).joinToString("\n")

        assertContains(violations, "file-level suppression is forbidden")
        assertContains(violations, "@LongMethod suppression is not registered")
        assertContains(violations, "exceeds registered maximum")
    }

    private fun java.nio.file.Path.writeSource(relativePath: String, source: String): java.io.File {
        val target = resolve(relativePath)
        target.parent.createDirectories()
        target.writeText(source)
        return target.toFile()
    }
}
