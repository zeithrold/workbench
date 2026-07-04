pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
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
    ":workbench-service",
    ":workbench-agile",
    ":workbench-tenant",
    ":workbench-data",
    ":workbench-security",
    ":workbench-web",
    ":workbench-worker",
    ":workbench-frontend",
)
