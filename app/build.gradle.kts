plugins { id("com.android.application"); kotlin("android"); id("com.google.devtools.ksp") }
android {
    namespace = "et.teleexpense.app"; compileSdk = 34
    defaultConfig { applicationId = "et.teleexpense.app"; minSdk = 26; targetSdk = 34; versionCode = 1; versionName = "0.1" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":parser"))
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3"); implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.room:room-runtime:2.6.1"); implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
}
