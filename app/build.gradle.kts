plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "br.com.maresdenatal"
    compileSdk = 35
    defaultConfig {
        applicationId = "br.com.maresdenatal"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}
dependencies {
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
