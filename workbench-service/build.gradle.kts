plugins {
  kotlin("plugin.spring")
  `java-test-fixtures`
}

dependencies {
  implementation(project(":workbench-core"))
  implementation("org.springframework:spring-context")
  implementation("org.springframework.boot:spring-boot-starter-mail")
  implementation("org.springframework:spring-web")
  testImplementation(project(":workbench-data"))
  testImplementation(libs.exposed.jdbc)
  testImplementation(libs.testcontainers.junit)
  testImplementation(libs.testcontainers.postgresql)
  testImplementation(libs.testcontainers.keycloak)
  testFixturesImplementation(project(":workbench-core"))
  testFixturesImplementation(project(":workbench-data"))
  testFixturesImplementation(libs.exposed.jdbc)
  testFixturesImplementation(libs.flyway.postgresql)
  testFixturesImplementation(libs.kotlinx.serialization.json)
  testFixturesImplementation(libs.testcontainers.junit)
  testFixturesImplementation(libs.testcontainers.postgresql)
  testFixturesImplementation(libs.testcontainers.keycloak)
}

sourceSets.test.get().resources.srcDir(rootProject.file("config/integration-test"))

sourceSets.testFixtures.get().resources.srcDir(rootProject.file("config/integration-test"))
