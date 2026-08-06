plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(project(":runtime:models"))
    implementation(project(":runtime:inference"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
