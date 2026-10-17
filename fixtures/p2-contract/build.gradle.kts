import java.security.MessageDigest
plugins { id("com.android.library"); kotlin("android") }
android {
    namespace = "fr.jarvis.fixture.p2"
    compileSdk = 36
    defaultConfig { minSdk = 29
        buildConfigField("String","LEGACY_CONTRACT_SHA","\"fab6a66a6b99302884849cd1d8aef97cd761082dc20680a4b036d7bc99d5c3c9\"")
        val wireDigest=MessageDigest.getInstance("SHA-256")
        val wireFiles=(listOf(file("CONTRACT.txt"),file("TABLES.json"))+fileTree("src/main/aidl").matching { include("**/*.aidl") }.files).sortedBy { it.relativeTo(projectDir).invariantSeparatorsPath }
        for (f in wireFiles) { wireDigest.update(f.relativeTo(projectDir).invariantSeparatorsPath.toByteArray(Charsets.UTF_8)); wireDigest.update(0.toByte()); wireDigest.update(f.readBytes()); wireDigest.update(0.toByte()) }
        buildConfigField("String","CONTRACT_SHA","\""+wireDigest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }+"\"")
    }
    buildFeatures { aidl = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { api(project(":domain")) }
