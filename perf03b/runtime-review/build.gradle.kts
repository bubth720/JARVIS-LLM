plugins { kotlin("jvm") }
kotlin {
    jvmToolchain(21)
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    // Compile the applied, hash-guarded backend. The PERF-03A postimage copy is never a source directory.
    sourceSets.getByName("main").kotlin.srcDir(rootProject.file("app/src/main/java/fr/jarvis/llm/engine"))
    sourceSets.getByName("main").kotlin.srcDir(layout.buildDirectory.dir("runtime-support"))
    sourceSets.getByName("test").kotlin.srcDir(rootProject.file("perf03/kotlin-review/src/test/kotlin"))
    sourceSets.getByName("test").kotlin.srcDir(layout.buildDirectory.dir("runtime-historical-tests"))
}
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
val copyFrozenProcessResources by tasks.registering(Copy::class) {
    from(rootProject.file("app/src/main/java/fr/jarvis/llm/ProcessResources.kt"))
    into(layout.buildDirectory.dir("runtime-support"))
}
tasks.named("compileKotlin") { dependsOn(copyFrozenProcessResources) }
val copyFrozenBackendTests by tasks.registering(Copy::class) {
    from(rootProject.file("app/src/test/java/fr/jarvis/llm/NativeBackendLifecycleTest.kt"))
    from(rootProject.file("app/src/test/java/fr/jarvis/llm/NativeFixtureBudgetTest.kt"))
    into(layout.buildDirectory.dir("runtime-historical-tests"))
}
tasks.named("compileTestKotlin") { dependsOn(copyFrozenBackendTests) }
dependencies {
    implementation(project(":domain"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}
tasks.test { testLogging { events("passed", "failed", "skipped") } }
