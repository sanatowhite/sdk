plugins {
    alias(libs.plugins.sanato.android.library.compose)
}

android {
    namespace = "io.sanato.apptemplate.debugtools"
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":core-net"))
    implementation(project(":core-telemetry"))
}
