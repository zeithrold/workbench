plugins {
  kotlin("plugin.spring")
}

dependencies {
  implementation(project(":workbench-core"))
  implementation(libs.exposed.core)
  implementation(libs.exposed.jdbc)
  implementation(libs.exposed.json)
  implementation(libs.exposed.kotlin.datetime)
  implementation(libs.exposed.java.time)
  implementation(libs.postgresql)
  implementation(libs.flyway.postgresql)
  implementation(libs.redisson)
  implementation("org.springframework.boot:spring-boot-starter-jdbc")
  implementation("org.springframework.boot:spring-boot-starter-data-redis")
  implementation("org.springframework.kafka:spring-kafka")
  testImplementation(libs.testcontainers.junit)
  testImplementation(libs.testcontainers.postgresql)
  testImplementation(libs.testcontainers.kafka)
  testImplementation(libs.testcontainers.elasticsearch)
}
