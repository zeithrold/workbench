plugins {
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.kotlin.spring)
}

dependencies {
  implementation(project(":workbench-core"))
  implementation(project(":workbench-service"))
  implementation(project(":workbench-agile"))
  implementation(project(":workbench-tenant"))
  implementation(project(":workbench-data"))
  implementation("org.springframework.boot:spring-boot-starter")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
  implementation("org.springframework.boot:spring-boot-starter-flyway")
  implementation("org.springframework.kafka:spring-kafka")
  implementation(libs.logstash.logback)
  runtimeOnly(libs.postgresql)
  testImplementation(libs.testcontainers.junit)
  testImplementation(libs.testcontainers.postgresql)
  testImplementation(libs.testcontainers.kafka)
  testImplementation(testFixtures(project(":workbench-service")))
}

sourceSets.main.get().resources.srcDir(rootProject.file("config/logging"))

springBoot {
  mainClass.set("ink.doa.workbench.worker.WorkbenchWorkerApplicationKt")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
  archiveFileName.set("workbench-worker.jar")
}
