plugins {
  kotlin("plugin.spring")
  kotlin("kapt")
}

dependencies {
  kapt(
    platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}")
  )
  kapt("org.springframework.boot:spring-boot-configuration-processor")
  implementation(project(":workbench-core"))
  implementation(libs.aws.s3)
  implementation("org.springframework.boot:spring-boot-starter")
  implementation(libs.exposed.core)
  implementation(libs.exposed.jdbc)
  implementation(libs.exposed.json)
  implementation(libs.exposed.kotlin.datetime)
  implementation(libs.exposed.java.time)
  implementation(libs.postgresql)
  implementation(libs.flyway.postgresql)
  implementation("org.flywaydb:flyway-core")
  implementation(libs.redisson)
  implementation("org.springframework.boot:spring-boot-starter-jdbc")
  implementation("org.springframework.boot:spring-boot-starter-data-redis")
  implementation("org.springframework.kafka:spring-kafka")
  testImplementation(project(":workbench-test-support"))
  testImplementation(libs.testcontainers.junit)
  testImplementation(libs.testcontainers.postgresql)
  testImplementation(libs.testcontainers.kafka)
  testImplementation(libs.testcontainers.elasticsearch)
  testImplementation(libs.testcontainers.minio)
}
