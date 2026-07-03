import org.gradle.api.tasks.testing.Test

dependencies { implementation(libs.ulid) }

tasks.named<Test>("test") {
  useJUnitPlatform { excludeTags("fuzz") }
}

tasks.register<Test>("fuzzVerification") {
  group = "verification"
  description = "Runs property-based (fuzz) tests"
  testClassesDirs = sourceSets.test.get().output.classesDirs
  classpath = sourceSets.test.get().runtimeClasspath
  useJUnitPlatform { includeTags("fuzz") }
}

kover {
  currentProject {
    instrumentation { disabledForTestTasks.add("fuzzVerification") }
  }
}
