plugins {
  kotlin("plugin.spring")
  `java-test-fixtures`
}

dependencies {
  implementation(project(":workbench-core"))
  implementation(project(":workbench-agile"))
  implementation(project(":workbench-security"))
  implementation(project(":workbench-tenant"))
  implementation("org.springframework:spring-context")
  implementation("org.springframework.boot:spring-boot-starter-mail")
  implementation("org.springframework:spring-web")
  testImplementation(project(":workbench-data"))
  testImplementation(testFixtures(project(":workbench-security")))
  testImplementation(libs.exposed.jdbc)
  testImplementation(libs.testcontainers.junit)
  testImplementation(libs.testcontainers.postgresql)
  testImplementation(libs.testcontainers.keycloak)
  testImplementation("org.springframework.security:spring-security-crypto")
  testFixturesImplementation(project(":workbench-core"))
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
