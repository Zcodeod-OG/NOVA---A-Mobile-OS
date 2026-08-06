plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(project(":runtime:models"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.onnxruntime.android)
    testImplementation(libs.junit)
}
