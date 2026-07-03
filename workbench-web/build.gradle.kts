plugins {
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.kotlin.spring)
}

dependencies {
  implementation(project(":workbench-core"))
  implementation(project(":workbench-service"))
  implementation(project(":workbench-agile"))
  implementation(project(":workbench-data"))
  implementation(project(":workbench-security"))
  implementation("org.springframework.boot:spring-boot-starter-webmvc")
  implementation("org.springframework.boot:spring-boot-starter-validation")
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework.boot:spring-boot-starter-aspectj")
  implementation("org.springframework.boot:spring-boot-starter-actuator")
  implementation("org.springframework.boot:spring-boot-starter-flyway")
  implementation(libs.jackson.module.kotlin)
  implementation(libs.springdoc.openapi)
  implementation(libs.scalar.webmvc)
  implementation(libs.logstash.logback)
  runtimeOnly(libs.kotlinx.coroutines.reactor)
  runtimeOnly(libs.postgresql)
  testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
  testImplementation("org.springframework.boot:spring-boot-starter-security-test")
  testImplementation(libs.testcontainers.junit)
  testImplementation(libs.testcontainers.postgresql)
  testImplementation(libs.testcontainers.keycloak)
  testImplementation(libs.exposed.jdbc)
  testImplementation(testFixtures(project(":workbench-service")))
  testImplementation(libs.redisson)
}

sourceSets.test.get().resources.srcDir(rootProject.file("config/integration-test"))

springBoot {
  mainClass.set("doa.ink.workbench.web.WorkbenchApplicationKt")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
  archiveFileName.set("workbench-web.jar")
}
