plugins { kotlin("jvm") }
kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    sourceSets.getByName("main").kotlin.srcDir(rootProject.file("perf03/candidate/engine"))
    sourceSets.getByName("main").kotlin.srcDir(layout.buildDirectory.dir("review-support"))
    sourceSets.getByName("test").kotlin.srcDir(layout.buildDirectory.dir("review-historical-tests"))
}
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
val copyFrozenProcessResources by tasks.registering(Copy::class) {
    from(rootProject.file("app/src/main/java/fr/jarvis/llm/ProcessResources.kt"))
    into(layout.buildDirectory.dir("review-support"))
}
tasks.named("compileKotlin") { dependsOn(copyFrozenProcessResources) }
val copyFrozenBackendTests by tasks.registering(Copy::class) {
    from(rootProject.file("app/src/test/java/fr/jarvis/llm/NativeBackendLifecycleTest.kt"))
    from(rootProject.file("app/src/test/java/fr/jarvis/llm/NativeFixtureBudgetTest.kt"))
    into(layout.buildDirectory.dir("review-historical-tests"))
}
tasks.named("compileTestKotlin") { dependsOn(copyFrozenBackendTests) }
dependencies {
    implementation(project(":domain"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
tasks.test { testLogging { events("passed", "failed", "skipped") } }
