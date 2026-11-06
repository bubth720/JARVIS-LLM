plugins { id("com.android.application"); kotlin("android") }

android {
    namespace = "fr.jarvis.llm.perf03b.lab"
    compileSdk = 36
    ndkVersion = "28.1.13356709"
    buildFeatures { buildConfig = true }
    defaultConfig {
        applicationId = "fr.jarvis.llm.perf03b.lab"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1-perf03b-lab"
        val revision = providers.exec { commandLine("git", "rev-parse", "HEAD"); isIgnoreExitValue = true }
            .standardOutput.asText.get().trim().ifBlank { "UNKNOWN" }
        buildConfigField("String", "SOURCE_COMMIT", "\"$revision\"")
        buildConfigField("String", "APPROVED_PATCH_SHA256", "\"747598dd77bc53e1a2f8df7f2e60be824ff74f687d355b060e2012b55a665460\"")
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake {
            arguments += "-DLLAMA_SOURCE_DIR=${rootDir}/.deps/llama.cpp"
            cppFlags += "-std=c++17"
        } }
    }
    externalNativeBuild { cmake {
        // Compile the applied, approved runtime, not the PERF-03A review copy.
        path = rootProject.file("app/src/main/cpp/CMakeLists.txt")
        version = "3.22.1"
    } }
    sourceSets.getByName("main").java.srcDir(layout.buildDirectory.dir("applied-engine-sources"))
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { release { isMinifyEnabled = false } }
    signingConfigs.getByName("debug") { storeFile = rootProject.file(".tools/perf03b-lab.keystore") }
    lint { abortOnError = true }
}

dependencies {
    implementation(project(":domain"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

val copyAppliedEngine by tasks.registering(Copy::class) {
    from(rootProject.file("app/src/main/java")) {
        include("fr/jarvis/llm/engine/LlamaBackend.kt")
        include("fr/jarvis/llm/engine/OwnedStreamContract.kt")
        include("fr/jarvis/llm/ProcessResources.kt")
    }
    into(layout.buildDirectory.dir("applied-engine-sources"))
}
tasks.named("preBuild") { dependsOn(copyAppliedEngine) }

val prepareLabDebugKeystore by tasks.registering {
    val key = rootProject.file(".tools/perf03b-lab.keystore")
    outputs.file(key)
    doLast {
        if (!key.exists()) {
            key.parentFile.mkdirs()
            project.exec {
                commandLine("${System.getProperty("java.home")}/bin/keytool", "-genkeypair", "-keystore", key.absolutePath,
                    "-storepass", "android", "-keypass", "android", "-alias", "androiddebugkey",
                    "-dname", "CN=JARVIS PERF03B Lab,O=Laboratory,C=FR", "-keyalg", "RSA", "-keysize", "2048",
                    "-validity", "3650", "-noprompt")
            }
        }
    }
}
tasks.matching { it.name == "validateSigningDebug" }.configureEach { dependsOn(prepareLabDebugKeystore) }
