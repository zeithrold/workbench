plugins {
  kotlin("plugin.spring")
  `java-test-fixtures`
}

dependencies {
  implementation(project(":workbench-kernel"))
  implementation(project(":workbench-identity"))
  implementation(libs.owasp.html.sanitizer)
  implementation(libs.jsoup)
  implementation("org.springframework:spring-context")
  implementation("org.springframework.boot:spring-boot")
  implementation("org.slf4j:slf4j-api")
  testImplementation(testFixtures(project(":workbench-agile")))
  testFixturesImplementation(project(":workbench-kernel"))
  testFixturesImplementation(project(":workbench-identity"))
  testFixturesImplementation(libs.kotlinx.coroutines.core)
  testFixturesImplementation(libs.kotlinx.serialization.json)
  testFixturesImplementation(libs.mockk)
}
