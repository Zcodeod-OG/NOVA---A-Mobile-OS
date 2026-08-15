plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.nova.runtime.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nova.runtime.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.1.0-sprint0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":runtime:kernel"))
    implementation(project(":runtime:conversation"))
    implementation(project(":runtime:understanding"))
    implementation(project(":runtime:inference"))
    implementation(project(":runtime:memory"))
    implementation(project(":runtime:reasoning"))
    implementation(project(":runtime:planner"))
    implementation(project(":runtime:execution"))
    implementation(project(":runtime:policy"))
    implementation(project(":runtime:capability"))
    implementation(project(":runtime:android-adapter"))
    implementation(project(":runtime:ai-core"))
    implementation(project(":runtime:ai-native"))
    implementation(project(":runtime:storage"))
    implementation(project(":runtime:orchestrator"))
    implementation(project(":runtime:events"))
    implementation(project(":runtime:models"))
    implementation(project(":runtime:utils"))

    implementation(libs.kotlin.stdlib)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:${libs.versions.lifecycle.get()}")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:${libs.versions.lifecycle.get()}")
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
