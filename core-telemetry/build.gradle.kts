plugins {
    alias(libs.plugins.sanato.android.library)
}

android {
    namespace = "io.sanato.apptemplate.core.telemetry"
}

dependencies {
    implementation(project(":core-common"))

    implementation(libs.androidx.metrics.performance)
}
