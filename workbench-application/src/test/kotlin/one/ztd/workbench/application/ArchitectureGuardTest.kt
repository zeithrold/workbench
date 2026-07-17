package one.ztd.workbench.application

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText

class ArchitectureGuardTest :
  StringSpec({
    "application production code stays in the application package" {
      applicationSourceFiles()
        .filterNot { file ->
          file.readText().lineSequence().firstOrNull()?.startsWith(APPLICATION_PACKAGE) == true
        }
        .map { file -> file.toRelativeDisplay() }
        .shouldBeEmpty()
    }

    "application production code does not depend on outbound or inbound adapters" {
      applicationSourceFiles()
        .flatMap { file ->
          file
            .readText()
            .lineSequence()
            .withIndex()
            .filter { (_, line) -> line.isForbiddenAdapterDependency() }
            .map { (index, line) -> "${file.toRelativeDisplay()}:${index + 1}: $line" }
        }
        .shouldBeEmpty()
    }
  })

private const val APPLICATION_PACKAGE = "package one.ztd.workbench.application"

private fun applicationSourceFiles(): List<Path> =
  Files.walk(workbenchApplicationDir().resolve("src/main/kotlin/one/ztd/workbench/application"))
    .use { paths ->
      paths.filter { path -> path.isRegularFile() && path.name.endsWith(".kt") }.toList()
    }

private fun workbenchApplicationDir(): Path {
  val current = Path.of("").toAbsolutePath()
  return if (current.fileName.toString() == "workbench-application") current
  else current.resolve("workbench-application")
}

private fun String.isForbiddenAdapterDependency(): Boolean {
  val trimmed = trim()
  return trimmed.startsWith("import one.ztd.workbench.data.") ||
    trimmed.startsWith("import one.ztd.workbench.security.") ||
    trimmed.startsWith("import one.ztd.workbench.web.") ||
    trimmed.startsWith("import one.ztd.workbench.worker.")
}

private fun Path.toRelativeDisplay(): String = workbenchApplicationDir().relativize(this).toString()
