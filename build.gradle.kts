plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.pitest) apply false
    alias(libs.plugins.node) apply false
    id("workbench.root-conventions")
    id("workbench.ci-conventions")
}

group = "one.ztd.workbench"
version = "0.1.0-SNAPSHOT"
