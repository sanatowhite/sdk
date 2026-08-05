plugins {
    alias(libs.plugins.sanato.android.library.compose)
    alias(libs.plugins.sanato.android.roborazzi)
}

android {
    namespace = "io.sanato.apptemplate.core.ui"
}

dependencies {
    implementation(project(":core-common"))
}
