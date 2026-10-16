pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "JarvisLlm"
include(":app", ":domain")

include(":p2-contract", ":p2-endpoint", ":p2-client")
project(":p2-contract").projectDir = file("fixtures/p2-contract")
project(":p2-endpoint").projectDir = file("fixtures/p2-endpoint")
project(":p2-client").projectDir = file("fixtures/p2-client")
