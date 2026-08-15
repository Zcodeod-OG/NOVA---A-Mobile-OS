plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.nova.runtime.android"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.core.ktx)
    implementation(project(":runtime:models"))
    implementation(project(":runtime:ai-core"))
    implementation(project(":runtime:capability"))
    implementation(project(":runtime:storage"))
    implementation(project(":runtime:utils"))
    implementation(libs.koin.android)
    implementation(libs.kotlinx.coroutines.android)
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(project(":runtime:events"))
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
