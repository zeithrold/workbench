package workbench.gradle

import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

internal object TestArchitectureConventions {
    private val moduleNames = backendProjectPaths.map { it.removePrefix(":") } + "workbench-test-support"
    private val integrationTag = Regex("""@Tags\(\s*"integration"\s*\)|@Tag\(\s*"integration"\s*\)""")

    fun inspect(rootDirectory: File): List<String> = inspect(rootDirectory, discoverTestFiles(rootDirectory))

    fun inspect(rootDirectory: File, testFiles: Iterable<File>): List<String> =
        testFiles.flatMap { file -> inspectFile(rootDirectory, file) }

    private fun discoverTestFiles(rootDirectory: File): List<File> =
        moduleNames.flatMap { moduleName ->
            listOf("test", "integrationTest").flatMap { sourceSet ->
                rootDirectory
                    .resolve("$moduleName/src/$sourceSet/kotlin")
                    .walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .toList()
            }
        }

    private fun inspectFile(rootDirectory: File, file: File): List<String> {
        val source = file.readText()
        val relativePath = file.relativeTo(rootDirectory).invariantSeparatorsPath
        val className = file.nameWithoutExtension
        val isIntegrationSource = "/src/integrationTest/" in "/$relativePath"
        val hasIntegrationTag = integrationTag.containsMatchIn(source)
        val usesWebMvcTest = "@WebMvcTest" in source
        val usesSpringBootTest = "@SpringBootTest" in source
        val usesSpringTest = usesWebMvcTest || usesSpringBootTest
        val violations = mutableListOf<String>()

        if (hasIntegrationTag) {
            violations +=
                "$relativePath: integration tags are retired; place the test under src/integrationTest."
        }
        if (!isIntegrationSource && className.endsWith("IntegrationTest")) {
            violations += "$relativePath: *IntegrationTest must be placed under src/integrationTest."
        }
        if (isIntegrationSource && className.endsWith("Test") && !className.endsWith("IntegrationTest")) {
            violations +=
                "$relativePath: tests under src/integrationTest must use the *IntegrationTest suffix."
        }
        if (className.endsWith("DirectTest")) {
            violations += "$relativePath: *DirectTest is retired; use *ControllerUnitTest for direct controller tests."
        }
        if (className.endsWith("ControllerUnitTest") && usesSpringTest) {
            violations += "$relativePath: *ControllerUnitTest must not start a Spring test context."
        }
        if (usesSpringTest && "io.kotest.core.spec.style." in source) {
            violations += "$relativePath: @WebMvcTest and @SpringBootTest must use JUnit 5, not a Kotest Spec."
        }
        if (usesSpringTest && "org.junit.jupiter.api.Test" !in source) {
            violations += "$relativePath: @WebMvcTest and @SpringBootTest must import org.junit.jupiter.api.Test."
        }
        if (usesSpringBootTest && !isIntegrationSource) {
            violations += "$relativePath: @SpringBootTest must be placed under src/integrationTest."
        }
        if (usesWebMvcTest && isIntegrationSource) {
            violations += "$relativePath: @WebMvcTest is a unit HTTP slice and must be placed under src/test."
        }
        if ("@AutoConfigureMockMvc" in source && "WebEnvironment.RANDOM_PORT" in source) {
            violations += "$relativePath: MockMvc tests must use WebEnvironment.MOCK; reserve RANDOM_PORT for real HTTP clients."
        }
        return violations
    }
}

abstract class TestArchitectureCheckTask : DefaultTask() {
    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testSources: ConfigurableFileCollection

    init {
        group = "verification"
        description = "Validates Workbench test naming, layering, and framework conventions."
    }

    @TaskAction
    fun verify() {
        val violations =
            TestArchitectureConventions.inspect(rootDirectory.get().asFile, testSources.files)
        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Test architecture convention violations:")
                    violations.forEach { appendLine("- $it") }
                }
            )
        }
    }
}
