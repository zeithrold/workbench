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

internal object ApiControllerVersionConventions {
    private val restController =
        Regex("""@(?:org\.springframework\.web\.bind\.annotation\.)?RestController\b""")
    private val packageDeclaration = Regex("""(?m)^package\s+([A-Za-z][A-Za-z0-9_.]*)\s*$""")
    private val controllerClass =
        Regex(
            """(?m)^\s*(?:(?:public|internal|open|final|abstract)\s+)*class\s+""" +
                """([A-Z][A-Za-z0-9]*Controller)\b"""
        )
    private val legacyControllerName = Regex("""^(.+)V(\d{8})Controller$""")
    private val versionedControllerName = Regex("""^.+V\d+Controller$""")

    fun inspect(rootDirectory: File, sourceFiles: Iterable<File>): List<String> {
        val controllers =
            sourceFiles
                .filter { it.isFile && it.extension == "kt" }
                .mapNotNull { inspectControllerSource(rootDirectory, it) }
                .toList()
        val currentControllers =
            controllers.filterNot(ControllerSource::legacy).associateBy { it.qualifiedName }
        val violations = controllers.flatMap(ControllerSource::violations).toMutableList()

        controllers.filter(ControllerSource::legacy).forEach { legacy ->
            val match = legacyControllerName.matchEntire(legacy.className) ?: return@forEach
            val currentClassName = "${match.groupValues[1]}Controller"
            val currentPackage = legacy.packageName.removeSuffix(".legacy")
            val currentQualifiedName = "$currentPackage.$currentClassName"
            if (currentQualifiedName !in currentControllers) {
                violations +=
                    "${legacy.relativePath}: legacy controller ${legacy.className} must have " +
                        "current counterpart $currentQualifiedName"
            }
        }

        return violations.sorted()
    }

    private fun inspectControllerSource(rootDirectory: File, file: File): ControllerSource? {
        val source = file.readText()
        val annotationCount = restController.findAll(source).count()
        if (annotationCount == 0) return null

        val relativePath = file.relativeTo(rootDirectory).invariantSeparatorsPath
        val packageName = packageDeclaration.find(source)?.groupValues?.get(1).orEmpty()
        val classNames = controllerClass.findAll(source).map { it.groupValues[1] }.toList()
        val className = classNames.singleOrNull() ?: file.nameWithoutExtension
        val legacy = packageName.endsWith(".legacy")
        val violations = mutableListOf<String>()

        if (annotationCount != 1) {
            violations += "$relativePath: controller source must declare exactly one @RestController"
        }
        if (classNames.size != 1) {
            violations +=
                "$relativePath: controller source must declare exactly one class ending in Controller"
        }
        if (file.nameWithoutExtension != className) {
            violations +=
                "$relativePath: controller file name must match class name $className"
        }

        val legacyNameMatch = legacyControllerName.matchEntire(className)
        if (legacy) {
            if (legacyNameMatch == null) {
                violations +=
                    "$relativePath: legacy controller must use {Resource}VyyyyMMddController naming"
            }
            if (!hasDeprecatedController(source, className)) {
                violations += "$relativePath: legacy controller $className must be annotated @Deprecated"
            }
        } else {
            if (versionedControllerName.matches(className)) {
                violations += "$relativePath: versioned controller $className must be in a legacy package"
            }
            if (hasDeprecatedController(source, className)) {
                violations +=
                    "$relativePath: current controller $className must not be annotated @Deprecated"
            }
        }

        return ControllerSource(
            relativePath = relativePath,
            packageName = packageName,
            className = className,
            legacy = legacy,
            violations = violations,
        )
    }

    private fun hasDeprecatedController(source: String, className: String): Boolean =
        Regex(
                """@Deprecated\s*(?:\([^)]*\))?""" +
                    """(?:\s|@[A-Za-z0-9_.]+(?:\([^)]*\))?)*""" +
                    """(?:public\s+|internal\s+|open\s+|final\s+|abstract\s+)*""" +
                    """class\s+${Regex.escape(className)}\b""",
                RegexOption.DOT_MATCHES_ALL,
            )
            .containsMatchIn(source)

    private data class ControllerSource(
        val relativePath: String,
        val packageName: String,
        val className: String,
        val legacy: Boolean,
        val violations: List<String>,
    ) {
        val qualifiedName: String = "$packageName.$className"
    }
}

abstract class ApiControllerVersionCheckTask : DefaultTask() {
    @get:Internal abstract val rootDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val controllerSources: ConfigurableFileCollection

    init {
        group = "verification"
        description = "Validates current and legacy API controller source layout."
    }

    @TaskAction
    fun verify() {
        val violations =
            ApiControllerVersionConventions.inspect(
                rootDirectory.get().asFile,
                controllerSources.files,
            )
        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("API controller version convention violations:")
                    violations.forEach { appendLine("- $it") }
                }
            )
        }
    }
}
