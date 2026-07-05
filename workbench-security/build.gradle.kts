plugins {
  kotlin("plugin.spring")
  kotlin("kapt")
  `java-test-fixtures`
}

dependencies {
  kapt(
    platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
  )
  kapt("org.springframework.boot:spring-boot-configuration-processor")
  implementation(project(":workbench-core"))
  implementation(project(":workbench-tenant"))
  implementation("org.springframework.boot:spring-boot-starter-security")
  implementation("org.springframework:spring-context")
  implementation("org.springframework.boot:spring-boot-starter-mail")
  implementation("org.springframework:spring-web")
  compileOnly("jakarta.servlet:jakarta.servlet-api")
  testImplementation("jakarta.servlet:jakarta.servlet-api")
  testImplementation("org.springframework:spring-test")
  testImplementation(project(":workbench-data"))
  testImplementation(project(":workbench-test-support"))
  testImplementation(libs.exposed.jdbc)
  testImplementation(libs.testcontainers.junit)
  testImplementation(libs.testcontainers.postgresql)
  testImplementation(libs.testcontainers.keycloak)
  testImplementation("org.springframework.security:spring-security-crypto")
  testFixturesImplementation(project(":workbench-core"))
  testFixturesImplementation(project(":workbench-data"))
  testFixturesImplementation(project(":workbench-test-support"))
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
  testFixturesImplementation(libs.unboundid.ldapsdk)
}

sourceSets.test.get().resources.srcDir(rootProject.file("config/integration-test"))

sourceSets.testFixtures.get().resources.srcDir(rootProject.file("config/integration-test"))
