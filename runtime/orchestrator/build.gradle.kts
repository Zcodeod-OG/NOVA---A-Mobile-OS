plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(project(":runtime:models"))
    implementation(project(":runtime:events"))
    implementation(project(":runtime:utils"))
    implementation(project(":runtime:kernel"))
    implementation(project(":runtime:understanding"))
    implementation(project(":runtime:reasoning"))
    implementation(project(":runtime:planner"))
    implementation(project(":runtime:execution"))
    implementation(project(":runtime:policy"))
    implementation(project(":runtime:memory"))
    implementation(libs.koin.core)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter.engine)
    testImplementation("org.junit.jupiter:junit-jupiter-params:${libs.versions.junit.jupiter.get()}")
    testImplementation(project(":runtime:capability"))
    testImplementation(project(":runtime:inference"))
}

tasks.test {
    useJUnitPlatform()
}
