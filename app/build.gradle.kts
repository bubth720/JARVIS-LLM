plugins { id("com.android.application"); kotlin("android") }
android {
    namespace = "fr.jarvis.llm"
    compileSdk = 36
    ndkVersion = "28.1.13356709"
    defaultConfig {
        applicationId = "fr.jarvis.llm"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-mission002"
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake { arguments += listOf("-DLLAMA_SOURCE_DIR=${rootDir}/.deps/llama.cpp"); cppFlags += "-std=c++17" } }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { release { isMinifyEnabled = false } }
    signingConfigs.getByName("debug") { storeFile = rootProject.file(".tools/debug.keystore") }
    lint { abortOnError = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { testTask ->
            // Keep Robolectric's runtime artifact cache writable in restricted cloud machines.
            testTask.systemProperty("user.home", rootProject.file(".tools/test-home").apply { mkdirs() }.absolutePath)
            testTask.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            for (name in listOf("http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort", "javax.net.ssl.trustStore")) {
                System.getProperty(name)?.let { value -> testTask.systemProperty(name, value) }
            }
        }
    }
}
dependencies {
    implementation(project(":domain"))
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
val prepareDebugKeystore by tasks.registering {
    val key = rootProject.file(".tools/debug.keystore")
    outputs.file(key)
    doLast {
        if (!key.exists()) {
            key.parentFile.mkdirs()
            // Public Android debug credentials only; never use this key for production distribution.
            project.exec {
                commandLine("${System.getProperty("java.home")}/bin/keytool", "-genkeypair", "-keystore", key.absolutePath,
                    "-storepass", "android", "-keypass", "android", "-alias", "androiddebugkey", "-dname", "CN=Android Debug,O=Android,C=US",
                    "-keyalg", "RSA", "-keysize", "2048", "-validity", "3650", "-noprompt")
            }
        }
    }
}
tasks.matching { it.name == "validateSigningDebug" }.configureEach { dependsOn(prepareDebugKeystore) }
