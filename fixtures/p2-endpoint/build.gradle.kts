plugins { id("com.android.application"); kotlin("android") }
val shareRuntime by tasks.registering(Sync::class) {
    from(rootProject.file("app/src/main/java/fr/jarvis/llm")) { include("engine/**", "ProcessResources.kt", "DeviceMonitor.kt", "ModelStore.kt") }
    into(layout.buildDirectory.dir("generated/shared-runtime/fr/jarvis/llm"))
}
val prepareFixtureKey by tasks.registering(Exec::class) {
    commandLine(rootProject.file("tools/fixture-keys.sh"))
    outputs.files(rootProject.file(".tools/fixture-common.keystore"),rootProject.file(".tools/fixture-refused.keystore"))
}
android {
    namespace = "fr.jarvis.fixture.llm"; compileSdk = 36; ndkVersion = "28.1.13356709"
    defaultConfig {
        applicationId = "fr.jarvis.fixture.llm"; minSdk = 29; targetSdk = 36; versionCode = 2; versionName = "003f-bootstrap-blocked"
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake { arguments += "-DLLAMA_SOURCE_DIR=${rootDir}/.deps/llama.cpp" } }
    }
    externalNativeBuild { cmake { path = rootProject.file("app/src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    sourceSets.getByName("main").java.srcDir(layout.buildDirectory.dir("generated/shared-runtime"))
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs.create("fixture") { storeFile = rootProject.file(".tools/fixture-common.keystore"); storePassword = "android"; keyAlias = "fixture"; keyPassword = "android" }
    buildTypes.getByName("debug").signingConfig = signingConfigs.getByName("fixture")
    lint { abortOnError = true }
    testOptions { unitTests.isIncludeAndroidResources = true
        unitTests.all { t ->
            t.systemProperty("user.home",rootProject.file(".tools/test-home").absolutePath)
            t.systemProperty("robolectric.dependency.repo.url","https://repo.maven.apache.org/maven2")
            for (n in listOf("http.proxyHost","http.proxyPort","https.proxyHost","https.proxyPort","javax.net.ssl.trustStore")) System.getProperty(n)?.let { t.systemProperty(n,it) }
        }
    }
}
tasks.named("preBuild") { dependsOn(shareRuntime) }
tasks.matching { it.name == "validateSigningDebug" }.configureEach { dependsOn(prepareFixtureKey) }
dependencies {
    implementation(project(":p2-contract"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
