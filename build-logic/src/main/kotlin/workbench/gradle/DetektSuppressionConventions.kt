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

internal object DetektSuppressionConventions {
    private data class Allowance(val rule: String, val maximumOccurrences: Int, val reason: String)

    private val allowances =
        mapOf(
            "workbench-jobs/src/main/kotlin/ink/doa/workbench/jobs/sprint/SprintCloseRequestedEventHandler.kt" to
                listOf(Allowance("TooGenericExceptionCaught", 1, "consumer failure boundary")),
            "workbench-jobs/src/main/kotlin/ink/doa/workbench/jobs/messaging/DomainEventExecutionService.kt" to
                listOf(Allowance("TooGenericExceptionCaught", 1, "shared handler execution boundary")),
            "workbench-jobs/src/main/kotlin/ink/doa/workbench/jobs/notification/EmailNotificationRelay.kt" to
                listOf(Allowance("TooGenericExceptionCaught", 1, "relay failure boundary")),
            "workbench-web/src/main/kotlin/ink/doa/workbench/web/messaging/PostgresEmbeddedJobs.kt" to
                listOf(Allowance("TooGenericExceptionCaught", 2, "listener and delivery failure boundaries")),
            "workbench-web/src/main/kotlin/ink/doa/workbench/web/messaging/RedisStreamsEmbeddedJobs.kt" to
                listOf(Allowance("TooGenericExceptionCaught", 2, "relay and delivery failure boundaries")),
            "workbench-web/src/main/kotlin/ink/doa/workbench/web/api/InfrastructureAspect.kt" to
                listOf(Allowance("TooGenericExceptionCaught", 1, "audit failure boundary")),
            "workbench-web/src/main/kotlin/ink/doa/workbench/web/admin/AdminUserController.kt" to
                listOf(Allowance("UnusedParameter", 5, "AOP request context binding")),
            "workbench-web/src/main/kotlin/ink/doa/workbench/web/instance/TenantAdminController.kt" to
                listOf(Allowance("UnusedParameter", 3, "AOP request context binding")),
            "workbench-web/src/main/kotlin/ink/doa/workbench/web/messaging/OutboxAdminController.kt" to
                listOf(
                    Allowance("UnusedParameter", 3, "AOP request context binding"),
                    Allowance("RedundantSuspendModifier", 3, "security aspect endpoint signature"),
                ),
            "workbench-web/src/main/kotlin/ink/doa/workbench/web/messaging/OutboxDeliveryAdminController.kt" to
                listOf(
                    Allowance("UnusedParameter", 2, "AOP request context binding"),
                    Allowance("RedundantSuspendModifier", 2, "security aspect endpoint signature"),
                ),
            "workbench-data/src/main/kotlin/ink/doa/workbench/data/persistence/postgres/ExposedColumnLookup.kt" to
                listOf(Allowance("UNCHECKED_CAST", 2, "Exposed dynamic column lookup")),
            "workbench-web/src/main/kotlin/ink/doa/workbench/web/api/ProjectRequestContextResolver.kt" to
                listOf(Allowance("UNCHECKED_CAST", 1, "Spring URI variable attribute type")),
            "workbench-service/src/testFixtures/kotlin/ink/doa/workbench/service/messaging/support/RecordingDomainEventPublisher.kt" to
                listOf(Allowance("UNCHECKED_CAST", 1, "generic recording fixture")),
            "workbench-test-support/src/main/kotlin/ink/doa/workbench/testsupport/postgres/PostgresDatabaseProvisioner.kt" to
                listOf(Allowance("SpreadOperator", 1, "Flyway Java vararg boundary")),
            "workbench-data/src/main/kotlin/ink/doa/workbench/data/storage/config/CoroutineDispatchersConfiguration.kt" to
                listOf(Allowance("InjectDispatcher", 1, "dispatcher composition root")),
            "workbench-web/src/test/kotlin/ink/doa/workbench/web/api/InfrastructureAspectTest.kt" to
                listOf(Allowance("DEPRECATION", 1, "deprecated annotation compatibility test")),
        )

    private val suppression = Regex("""@(?:file:)?Suppress\(([^)]*)\)""", setOf(RegexOption.DOT_MATCHES_ALL))
    private val ruleName = Regex("""\"([A-Za-z0-9_]+)\"""")

    fun inspect(rootDirectory: File, sourceFiles: Iterable<File>): List<String> =
        sourceFiles.flatMap { file -> inspectFile(rootDirectory, file) }

    private fun inspectFile(rootDirectory: File, file: File): List<String> {
        val source = file.readText()
        val relativePath = file.relativeTo(rootDirectory).invariantSeparatorsPath
        val violations = mutableListOf<String>()
        if ("@file:Suppress" in source) {
            violations += "$relativePath: file-level suppression is forbidden."
        }
        val counts = mutableMapOf<String, Int>()
        suppression.findAll(source).forEach { annotation ->
            ruleName.findAll(annotation.groupValues[1]).forEach { match ->
                val rule = match.groupValues[1]
                counts[rule] = counts.getOrDefault(rule, 0) + 1
            }
        }
        val allowed = allowances[relativePath].orEmpty().associateBy(Allowance::rule)
        counts.forEach { (rule, count) ->
            val allowance = allowed[rule]
            when {
                allowance == null -> violations += "$relativePath: @$rule suppression is not registered."
                count > allowance.maximumOccurrences ->
                    violations +=
                        "$relativePath: @$rule suppression count $count exceeds registered maximum " +
                            "${allowance.maximumOccurrences} (${allowance.reason})."
            }
        }
        return violations
    }
}

abstract class DetektSuppressionCheckTask : DefaultTask() {
    @get:Internal abstract val rootDirectory: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val kotlinSources: ConfigurableFileCollection

    init {
        group = "verification"
        description = "Rejects unregistered or broad Kotlin suppressions."
    }

    @TaskAction
    fun verify() {
        val violations = DetektSuppressionConventions.inspect(rootDirectory.get().asFile, kotlinSources.files)
        if (violations.isNotEmpty()) {
            throw GradleException(
                buildString {
                    appendLine("Detekt suppression governance violations:")
                    violations.forEach { appendLine("- $it") }
                }
            )
        }
    }
}
