plugins {
  alias(libs.plugins.spring.boot)
  alias(libs.plugins.spring.dependency.management)
  alias(libs.plugins.kotlin.spring)
  alias(libs.plugins.pitest)
}

dependencies {
  implementation(project(":workbench-core"))
  implementation(project(":workbench-service"))
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
  runtimeOnly(libs.postgresql)
  testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
  testImplementation("org.springframework.boot:spring-boot-starter-security-test")
  testImplementation(libs.testcontainers.junit)
  testImplementation(libs.testcontainers.postgresql)
}

springBoot {
  mainClass.set("doa.ink.workbench.web.WorkbenchApplicationKt")
}

pitest {
  junit5PluginVersion.set("1.2.2")
  targetClasses.set(setOf("doa.ink.workbench.*"))
  targetTests.set(setOf("doa.ink.workbench.*"))
  outputFormats.set(setOf("XML", "HTML"))
  mutationThreshold.set(1)
}
