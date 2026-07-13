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

internal object ModuleArchitectureConventions {
    private val allowedDependencies =
        mapOf(
            "workbench-kernel" to emptySet(),
            "workbench-tenant" to setOf("workbench-kernel"),
            "workbench-identity" to setOf("workbench-kernel", "workbench-tenant"),
            "workbench-agile" to setOf("workbench-kernel", "workbench-identity"),
            "workbench-notification" to setOf("workbench-kernel"),
            "workbench-application" to
                setOf(
                    "workbench-kernel",
                    "workbench-tenant",
                    "workbench-identity",
                    "workbench-agile",
                    "workbench-notification",
                ),
            "workbench-data" to
                setOf(
                    "workbench-kernel",
                    "workbench-tenant",
                    "workbench-identity",
                    "workbench-agile",
                    "workbench-notification",
                    "workbench-application",
                ),
            "workbench-security" to setOf("workbench-kernel", "workbench-tenant", "workbench-identity"),
            "workbench-web" to
                setOf(
                    "workbench-kernel",
                    "workbench-tenant",
                    "workbench-identity",
                    "workbench-agile",
                    "workbench-notification",
                    "workbench-application",
                    "workbench-data",
                    "workbench-security",
                ),
            "workbench-worker" to setOf("workbench-kernel", "workbench-application", "workbench-data"),
        )
    private val expectedPackageRoot =
        allowedDependencies.keys.associateWith { it.removePrefix("workbench-").replace('-', '.') }
    private val productionDependency =
        Regex("""(?:api|implementation|compileOnly|runtimeOnly)\(project\(\"[:]([^\"]+)\"\)\)""")
    private val forbiddenDomainImports =
        listOf(
            "org.springframework.web.",
            "jakarta.servlet.",
            "org.springframework.security.",
            "org.springframework.mail.",
            "org.springframework.kafka.",
            "org.redisson.",
            "io.lettuce.",
            "org.jetbrains.exposed.",
        )
    private val domainModules = setOf("workbench-tenant", "workbench-identity", "workbench-agile", "workbench-notification")

    fun inspect(rootDirectory: File): List<String> {
        val violations = mutableListOf<String>()
        allowedDependencies.forEach { (module, allowed) ->
            val moduleDirectory = rootDirectory.resolve(module)
            val buildFile = moduleDirectory.resolve("build.gradle.kts")
            if (buildFile.isFile) {
                productionDependency.findAll(buildFile.readText()).forEach { match ->
                    val dependency = match.groupValues[1]
                    if (dependency !in allowed) {
                        violations += "$module/build.gradle.kts: production dependency on $dependency is not allowed"
                    }
                }
            }
            moduleDirectory.resolve("src/main/kotlin").walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .forEach { file -> violations += inspectSource(rootDirectory, module, file) }
        }
        return violations
    }

    private fun inspectSource(rootDirectory: File, module: String, file: File): List<String> {
        val source = file.readText()
        val path = file.relativeTo(rootDirectory).invariantSeparatorsPath
        val expected = "package ink.doa.workbench.${expectedPackageRoot.getValue(module)}"
        val violations = mutableListOf<String>()
        if (!source.lineSequence().any { it == expected || it.startsWith("$expected.") }) {
            violations += "$path: package must be rooted at ${expected.removePrefix("package ")}"
        }
        if (module == "workbench-kernel" && "org.springframework." in source) {
            violations += "$path: kernel must not depend on Spring"
        }
        if (module in domainModules) {
            forbiddenDomainImports.filter(source::contains).forEach { forbidden ->
                violations += "$path: domain module must not reference $forbidden"
            }
        }
        if (module == "workbench-application") {
            listOf("ink.doa.workbench.data.", "ink.doa.workbench.security.", "ink.doa.workbench.web.", "ink.doa.workbench.worker.")
                .filter(source::contains)
                .forEach { forbidden -> violations += "$path: application must not reference $forbidden" }
        }
        if ("scanBasePackages = [\"ink.doa.workbench\"]" in source) {
            violations += "$path: full repository component scanning is forbidden"
        }
        return violations
    }
}

abstract class ModuleArchitectureCheckTask : DefaultTask() {
    @get:Internal abstract val rootDirectory: DirectoryProperty
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val architectureInputs: ConfigurableFileCollection

    init {
        group = "verification"
        description = "Validates Workbench module ownership and dependency direction."
    }

    @TaskAction
    fun verify() {
        val violations = ModuleArchitectureConventions.inspect(rootDirectory.get().asFile)
        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Module architecture convention violations:")
                    violations.forEach { appendLine("- $it") }
                }
            )
        }
    }
}
