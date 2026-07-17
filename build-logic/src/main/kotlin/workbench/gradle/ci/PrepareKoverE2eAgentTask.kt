package workbench.gradle.ci

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class PrepareKoverE2eAgentTask : DefaultTask() {
    @get:InputFiles
    abstract val agentJar: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun prepare() {
        val output = outputDir.asFile.get()
        output.mkdirs()

        val sourceJar =
            agentJar.files.singleOrNull()
                ?: error("Expected exactly one Kover JVM agent jar, found ${agentJar.files.size}")

        val targetJar = output.resolve(sourceJar.name)
        sourceJar.copyTo(targetJar, overwrite = true)

        val webReport = output.resolve("web.ic").absolutePath
        val workerReport = output.resolve("worker.ic").absolutePath
        val includeFilter = "one.ztd.workbench.*"

        output.resolve("web.args").writeText(
            """
            report.file=$webReport
            report.append=false
            include=$includeFilter
            """.trimIndent() + "\n",
        )
        output.resolve("worker.args").writeText(
            """
            report.file=$workerReport
            report.append=false
            include=$includeFilter
            """.trimIndent() + "\n",
        )

        val manifest =
            """
            {
              "agentJar": "${targetJar.absolutePath.replace("\\", "\\\\")}",
              "webArgs": "${output.resolve("web.args").absolutePath.replace("\\", "\\\\")}",
              "workerArgs": "${output.resolve("worker.args").absolutePath.replace("\\", "\\\\")}"
            }
            """.trimIndent()
        output.resolve("agent-manifest.json").writeText(manifest)

        logger.lifecycle("Prepared Kover E2E agent at ${targetJar.absolutePath}")
    }
}
