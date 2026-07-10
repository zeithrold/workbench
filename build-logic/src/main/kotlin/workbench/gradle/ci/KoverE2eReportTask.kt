package workbench.gradle.ci

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import workbench.gradle.backendProjectPaths

abstract class KoverE2eReportTask : DefaultTask() {
    @get:InputFiles
    abstract val cliJar: ConfigurableFileCollection

    @get:InputDirectory
    abstract val repoRoot: DirectoryProperty

    @get:InputDirectory
    abstract val e2eReportsDir: DirectoryProperty

    @get:OutputFile
    abstract val outputXml: RegularFileProperty

    @TaskAction
    fun report() {
        val root = repoRoot.asFile.get()
        val e2eDir = e2eReportsDir.asFile.get()
        val webReport = e2eDir.resolve("web.ic")
        val workerReport = e2eDir.resolve("worker.ic")
        require(webReport.isFile || workerReport.isFile) {
            "No Kover E2E binary reports found under ${e2eDir.absolutePath}"
        }

        val binaryReports = listOf(webReport, workerReport).filter { it.isFile }
        val classfiles =
            backendProjectPaths.flatMap { projectPath ->
                val module = root.resolve(projectPath.removePrefix(":"))
                listOf(
                    module.resolve("build/classes/kotlin/main"),
                    module.resolve("build/classes/java/main"),
                ).filter { it.isDirectory }
            }
        require(classfiles.isNotEmpty()) {
            "No compiled backend classes found. Run bootJar tasks before generating the E2E Kover report."
        }

        val sources =
            backendProjectPaths.flatMap { projectPath ->
                val module = root.resolve(projectPath.removePrefix(":"))
                listOf(
                    module.resolve("src/main/kotlin"),
                    module.resolve("src/main/java"),
                ).filter { it.isDirectory }
            }

        val cli =
            cliJar.files.singleOrNull()
                ?: error("Expected exactly one Kover CLI jar, found ${cliJar.files.size}")

        val command =
            buildList {
                add("java")
                add("-jar")
                add(cli.absolutePath)
                add("report")
                binaryReports.forEach { report -> add(report.absolutePath) }
                classfiles.forEach { classes -> add("--classfiles"); add(classes.absolutePath) }
                sources.forEach { src -> add("--src"); add(src.absolutePath) }
                add("--title")
                add("Workbench E2E coverage")
                add("--xml")
                add(outputXml.asFile.get().absolutePath)
            }

        val process =
            ProcessBuilder(command)
                .directory(root)
                .redirectErrorStream(true)
                .start()
        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (output.isNotBlank()) {
            logger.lifecycle(output.trim())
        }
        check(exitCode == 0) {
            "Kover CLI report failed with exit code $exitCode"
        }
        logger.lifecycle("Wrote Kover E2E XML report to ${outputXml.asFile.get()}")
    }
}
