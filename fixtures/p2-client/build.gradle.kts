plugins { id("com.android.application"); kotlin("android") }
android {
    namespace = "fr.jarvis.fixture.client"; compileSdk = 36
    defaultConfig { minSdk = 29; targetSdk = 36; versionCode = 1; versionName = "003e-identity-probe" }
    flavorDimensions += "identity"
    signingConfigs.create("common") { storeFile = rootProject.file(".tools/fixture-common.keystore"); storePassword="android"; keyAlias="fixture"; keyPassword="android" }
    signingConfigs.create("refused") { storeFile = rootProject.file(".tools/fixture-refused.keystore"); storePassword="android"; keyAlias="fixture"; keyPassword="android" }
    productFlavors {
        create("authorized") { dimension="identity"; applicationId="fr.jarvis.fixture.core"; signingConfig=signingConfigs.getByName("common") }
        create("wrongPackage") { dimension="identity"; applicationId="fr.jarvis.fixture.unknown"; signingConfig=signingConfigs.getByName("common") }
        create("wrongSignature") { dimension="identity"; applicationId="fr.jarvis.fixture.badclient"; signingConfig=signingConfigs.getByName("refused") }
    }
    buildTypes.getByName("debug").signingConfig=null
    compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget="17" }
    lint { abortOnError=true }
}
tasks.matching { it.name.startsWith("validateSigning") }.configureEach { dependsOn(":p2-endpoint:prepareFixtureKey") }
dependencies { implementation(project(":p2-contract")) }
