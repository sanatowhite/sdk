plugins {
    alias(libs.plugins.sanato.android.library)
}

android {
    namespace = "io.sanato.apptemplate.core.data"
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":core-net"))

    implementation(libs.androidx.datastore.preferences)
}
