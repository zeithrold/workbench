plugins {
  kotlin("plugin.spring")
  `java-test-fixtures`
}

dependencies {
  implementation(project(":workbench-core"))
  implementation("org.springframework:spring-context")
  implementation("org.slf4j:slf4j-api")
  testImplementation(testFixtures(project(":workbench-agile")))
  testFixturesImplementation(project(":workbench-core"))
  testFixturesImplementation(libs.kotlinx.coroutines.core)
  testFixturesImplementation(libs.kotlinx.serialization.json)
  testFixturesImplementation(libs.mockk)
}
