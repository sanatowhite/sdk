plugins {
    alias(libs.plugins.sanato.android.application)
    alias(libs.plugins.sanato.android.hilt)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.sanato.apptemplate"

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":core-ui"))
    implementation(project(":core-net"))
    implementation(project(":core-data"))
    implementation(project(":core-telemetry"))
    implementation(project(":updatechecker"))

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    debugImplementation(project(":debug-tools"))
}
