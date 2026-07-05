plugins {
  alias(libs.plugins.kotlin.jvm)
}

kotlin {
  jvmToolchain(25)
}

dependencies {
  implementation(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"))
  testImplementation(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"))
  implementation(libs.exposed.jdbc)
  implementation(libs.postgresql)
  implementation(libs.flyway.postgresql)
  implementation("org.flywaydb:flyway-core")
  implementation(libs.testcontainers.postgresql)
  testImplementation(libs.testcontainers.junit)
  implementation(libs.kotlinx.coroutines.core)
  compileOnly(
    platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
  )
  compileOnly("org.springframework:spring-test")
  compileOnly("org.springframework:spring-jdbc")

  testImplementation(libs.kotest.runner)
  testImplementation(libs.kotest.assertions)
  testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
}
