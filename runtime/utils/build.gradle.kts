plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":runtime:models"))
    implementation(libs.kotlin.stdlib)
    implementation(project(":runtime:models"))

    testImplementation(libs.kotlin.test.junit)
}

tasks.test {
    useJUnitPlatform()
}
