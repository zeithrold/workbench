package workbench.gradle.ci

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

abstract class MergePitReportsTask : DefaultTask() {
    @get:InputDirectory
    abstract val repoRoot: DirectoryProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun merge() {
        val root = repoRoot.asFile.get()
        val reports = QualityReports.discoverModulePitReports(root)
        val mutationCount = QualityReports.mergePitReports(reports, outputFile.asFile.get())
        logger.lifecycle(
            "Wrote aggregated PIT report for ${reports.size} modules ($mutationCount mutations) to " +
                outputFile.asFile.get(),
        )
    }
}
