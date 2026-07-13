plugins {
  kotlin("plugin.spring")
  `java-test-fixtures`
}

dependencies {
  implementation(project(":workbench-kernel"))
  implementation(project(":workbench-agile"))
  implementation(project(":workbench-tenant"))
  implementation(project(":workbench-identity"))
  implementation(project(":workbench-notification"))
  implementation("org.springframework:spring-context")
  implementation("org.springframework.boot:spring-boot")
  implementation("org.springframework:spring-context-support")
  implementation("org.slf4j:slf4j-api")
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(project(":workbench-data"))
  testImplementation(project(":workbench-test-support"))
  testImplementation(testFixtures(project(":workbench-security")))
  testImplementation(libs.exposed.jdbc)
  testImplementation(libs.testcontainers.junit)
  testImplementation(libs.testcontainers.postgresql)
  testImplementation(libs.testcontainers.keycloak)
  testImplementation("org.springframework.security:spring-security-crypto")
  testImplementation("org.springframework:spring-test")
  testFixturesImplementation(project(":workbench-kernel"))
  testFixturesImplementation(project(":workbench-identity"))
  testFixturesImplementation(project(":workbench-tenant"))
  testFixturesImplementation(project(":workbench-agile"))
  testFixturesImplementation(project(":workbench-data"))
  testFixturesImplementation(libs.exposed.jdbc)
  testFixturesImplementation(libs.flyway.postgresql)
  testFixturesImplementation(libs.kotlinx.serialization.json)
  testFixturesImplementation(libs.testcontainers.junit)
  testFixturesImplementation(libs.testcontainers.postgresql)
  testFixturesImplementation(libs.testcontainers.keycloak)
  testFixturesImplementation(
    platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
  )
  testFixturesImplementation(libs.kotlinx.coroutines.core)
  testFixturesImplementation(libs.testcontainers.kafka)
  testFixturesImplementation("org.springframework.kafka:spring-kafka")
}

sourceSets.test.get().resources.srcDir(rootProject.file("config/integration-test"))

sourceSets.testFixtures.get().resources.srcDir(rootProject.file("config/integration-test"))
