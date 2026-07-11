package workbench.gradle

import java.util.Properties
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension

internal val backendProjectPaths =
    listOf(
        ":workbench-core",
        ":workbench-service",
        ":workbench-agile",
        ":workbench-tenant",
        ":workbench-data",
        ":workbench-security",
        ":workbench-jobs",
        ":workbench-web",
        ":workbench-worker",
    )

internal val koverExcludedClasses =
    arrayOf(
        "*.WorkbenchApplication*",
        "*.WorkbenchWorkerApplication*",
        "*.api.*Configuration",
        "*.security.*Configuration",
        "*.infrastructure.persistence.*Configuration",
        "*.data.persistence.*Configuration",
    )

internal fun moduleLineCoverageFloor(moduleName: String): Int = 90

internal fun Project.libs(): VersionCatalog =
    rootProject.extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

internal fun Project.springBootBomNotation(): String =
    "org.springframework.boot:spring-boot-dependencies:${libs().findVersion("spring-boot").get()}"

internal class PitestProperties(private val rootProject: Project) {
    private val properties: Properties by lazy {
        Properties().apply {
            rootProject.file("config/pitest/pitest.properties").inputStream().use { load(it) }
        }
    }

    fun string(key: String): String =
        properties.getProperty(key) ?: error("Missing pitest property: $key")

    fun csv(key: String): Set<String> =
        string(key)
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
}
