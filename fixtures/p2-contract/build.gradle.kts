import java.security.MessageDigest
plugins { id("com.android.library"); kotlin("android") }
android {
    namespace = "fr.jarvis.fixture.p2"
    compileSdk = 36
    defaultConfig { minSdk = 29
        val wireDigest=MessageDigest.getInstance("SHA-256")
        val wireFiles=(listOf(file("CONTRACT.txt"))+fileTree("src/main/aidl").matching { include("**/*.aidl") }.files).sortedBy { it.relativeTo(projectDir).invariantSeparatorsPath }
        for (f in wireFiles) { wireDigest.update(f.relativeTo(projectDir).invariantSeparatorsPath.toByteArray(Charsets.UTF_8)); wireDigest.update(0.toByte()); wireDigest.update(f.readBytes()); wireDigest.update(0.toByte()) }
        buildConfigField("String","CONTRACT_SHA","\""+wireDigest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }+"\"")
    }
    buildFeatures { aidl = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { api(project(":domain")) }
