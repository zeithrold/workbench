plugins {
  kotlin("plugin.spring")
}

dependencies {
  implementation(project(":workbench-core"))
  implementation("org.springframework:spring-context")
  implementation("org.slf4j:slf4j-api")
}
