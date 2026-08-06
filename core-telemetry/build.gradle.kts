plugins {
    alias(libs.plugins.sanato.android.library)
}

android {
    namespace = "io.sanato.apptemplate.core.telemetry"
}

dependencies {
    implementation(project(":core-common"))

    implementation(libs.androidx.metrics.performance)

    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
}
