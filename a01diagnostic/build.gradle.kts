plugins { id("com.android.application") }
android {
 namespace = "fr.jarvis.a01diagnostic"
 compileSdk = 36
 defaultConfig { applicationId = "fr.jarvis.a01diagnostic"; minSdk = 29; targetSdk = 35; versionCode = 1; versionName = "0.1-diagnostic" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
