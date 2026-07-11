pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("com.gradle.develocity") version "4.5.0"
}

develocity {
    server.set("https://scans.gradle.com")

    buildScan {
        termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
        termsOfUseAgree.set("yes")
        uploadInBackground.set(System.getenv("CI") == null)
        tag(if (System.getenv("CI") == null) "LOCAL" else "CI")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        ivy {
            name = "Node.js"
            url = uri("https://nodejs.org/dist")
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("org.nodejs", "node")
            }
        }
    }
}

rootProject.name = "workbench"

include(
    ":workbench-core",
    ":workbench-test-support",
    ":workbench-service",
    ":workbench-agile",
    ":workbench-tenant",
    ":workbench-data",
    ":workbench-security",
    ":workbench-jobs",
    ":workbench-web",
    ":workbench-worker",
    ":workbench-frontend",
)
