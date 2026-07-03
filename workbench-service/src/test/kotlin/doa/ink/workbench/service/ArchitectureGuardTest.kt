package doa.ink.workbench.service

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText

class ArchitectureGuardTest :
  StringSpec({
    "service production code stays in the service package" {
      val violations =
        serviceSourceFiles()
          .filterNot { file ->
            file.readText().lineSequence().firstOrNull()?.startsWith(SERVICE_PACKAGE) == true
          }
          .map { file -> file.toRelativeDisplay() }

      violations.shouldBeEmpty()
    }

    "service production code does not depend on repositories or data implementations" {
      val violations =
        serviceSourceFiles().flatMap { file ->
          file
            .readText()
            .lineSequence()
            .withIndex()
            .filter { (_, line) -> line.isForbiddenServiceDependency() }
            .map { (index, line) -> "${file.toRelativeDisplay()}:${index + 1}: $line" }
        }

      violations.shouldBeEmpty()
    }

    "application service classes use the ApplicationService suffix" {
      val violations =
        serviceSourceFiles().flatMap { file ->
          Regex("""class\s+(\w+Service)\s*\(""").findAll(file.readText()).mapNotNull { match ->
            val className = match.groupValues[1]
            if (className.endsWith("ApplicationService")) {
              null
            } else {
              "${file.toRelativeDisplay()}: $className"
            }
          }
        }

      violations.shouldBeEmpty()
    }
  })

private const val SERVICE_PACKAGE = "package doa.ink.workbench.service"

private fun serviceSourceFiles(): List<Path> =
  Files.walk(workbenchServiceDir().resolve("src/main/kotlin/doa/ink/workbench/service")).use { paths
    ->
    paths.filter { path -> path.isRegularFile() && path.name.endsWith(".kt") }.toList()
  }

private fun workbenchServiceDir(): Path {
  val current = Path.of("").toAbsolutePath()
  return if (current.fileName.toString() == "workbench-service") {
    current
  } else {
    current.resolve("workbench-service")
  }
}

private fun String.isForbiddenServiceDependency(): Boolean {
  val trimmed = trim()
  return trimmed.startsWith("import doa.ink.workbench.data.") ||
    (trimmed.startsWith("import ") && trimmed.endsWith("Repository")) ||
    trimmed == "import doa.ink.workbench.core.identity.LoginAccountStore" ||
    Regex(""":\s*\w*Repository\b""").containsMatchIn(trimmed) ||
    Regex(""":\s*LoginAccountStore\b""").containsMatchIn(trimmed)
}

private fun Path.toRelativeDisplay(): String = workbenchServiceDir().relativize(this).toString()
