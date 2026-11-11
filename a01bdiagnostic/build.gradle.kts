plugins { id("com.android.application") }
android {
 namespace = "fr.jarvis.a01b"
 compileSdk = 36
 defaultConfig { applicationId = "fr.jarvis.a01b"; minSdk = 29; targetSdk = 35; versionCode = 1; versionName = "0.1-keytest" }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
