plugins {
  kotlin("plugin.spring")
}

dependencies {
  implementation(project(":workbench-core"))
  implementation(project(":workbench-service"))
  implementation(project(":workbench-agile"))
  implementation(project(":workbench-tenant"))
  implementation("org.springframework:spring-context")
  implementation("org.springframework.boot:spring-boot")
  implementation("org.slf4j:slf4j-api")
  implementation(libs.kotlinx.coroutines.core)
  testImplementation(project(":workbench-test-support"))
  testImplementation(testFixtures(project(":workbench-service")))
  testImplementation("org.springframework:spring-test")
}
