import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.spotless.gradle.plugin)
    implementation(libs.kover.gradle.plugin)
    implementation(libs.pitest.gradle.plugin)

    testImplementation(kotlin("test"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_24
    targetCompatibility = JavaVersion.VERSION_24
}

gradlePlugin {
    plugins {
        create("rootConventions") {
            id = "workbench.root-conventions"
            implementationClass = "workbench.gradle.RootConventionsPlugin"
        }
        create("backendConventions") {
            id = "workbench.backend-conventions"
            implementationClass = "workbench.gradle.BackendConventionsPlugin"
        }
        create("testingConventions") {
            id = "workbench.testing-conventions"
            implementationClass = "workbench.gradle.TestingConventionsPlugin"
        }
        create("ciConventions") {
            id = "workbench.ci-conventions"
            implementationClass = "workbench.gradle.CiConventionsPlugin"
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_24)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
